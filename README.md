# Tâche 2 : Génération de tests et analyse de mutation

## Référentiel et documentation

- Référentiel du projet : [Chrisamy/tika](https://github.com/Chrisamy/tika)
- Ce document : [README.md](https://github.com/Chrisamy/tika/blob/main/README.md)

## Objectif et périmètre

Cette étude porte sur une classe du module Maven `tika-serialization` :
`org.apache.tika.serialization.ComponentNameResolver`.

La classe a été retenue parce qu’elle possède déjà des tests, mais n’est pas
couverte entièrement. Le test
`ComponentNameResolverTest.unregisteredComponentGivesActionableMessage`
vérifie le rejet d’un nom de composant inconnu et la présence d’explications
utiles dans l’exception.
L’analyse JaCoCo de l’ensemble des tests du module indique une couverture
partielle, et PIT trouve des mutants vivants, répondant bien au
critère de sélection de la tâche.

## Couverture et mutants avec les tests existants

JaCoCo, exécuté avec les tests du module, rapporte pour
`ComponentNameResolver` :

| Mesure | Résultat |
| --- | ---: |
| Lignes couvertes | 92 / 117 (79 %) |
| Branches couvertes | 35 / 50 (70 %) |
 
PIT 1.30.0 et son adaptateur JUnit 5 1.2.3 ont été exécutés sur le module.
Le dernier rapport exploratoire ciblait l’ensemble des classes de
sérialisation et de chargement de configuration. Pour la classe choisie, le
rapport PIT indique :

| Mesure | Résultat |
| --- | ---: |
| Lignes mutées exécutées | 91 / 119 (76 %) |
| Mutants tués | 19 / 39 (49 %) |
| Mutants survivants | 10 / 39 |
| Mutants sans couverture | 10 / 39 |

Le résultat de 19 mutants tués sur 39 est la mesure propre à la classe; le
score global de 49 % sur 670 mutants (330 tués) concerne le périmètre
exploratoire plus large et n’est pas le score de la classe seule.

Les mutants tués concernent notamment les résultats de `resolveClass`,
`getComponentInfo`, `getFriendlyName`, `isWireBlocked` et
`determineContextKey`. Les tests existants exercent des chemins de résolution
et de désérialisation de ParseContext et vérifient leurs conséquences
observables : résultat ou exception, choix de clé, ainsi que l’autorisation ou
le blocage des clés de contexte.

Les survivants sont concentrés dans les comportements suivants :

| Méthode | Mutants survivants | Comportement à mieux vérifier |
| --- | ---: | --- |
| `unregisteredMessage` | 5 | Cas sans composant enregistré, séparateurs de la liste, limite de 50 noms et suffixe signalant les entrées omises |
| `getFriendlyName` | 1 | Valeur de repli lorsqu’aucun nom convivial n’est trouvé |
| `findContextKeyInterface` | 1 | Retour de l’interface correspondant à un type qui en implémente une |
| `usesCompactFormat` | 2 | Différence entre un type utilisant une clé de contexte compacte et un type ordinaire |
| `determineContextKey` | 1 | Repli sur la classe du composant quand aucune clé explicite ou interface ne s’applique |

Dix autres mutants n’ont pas été couverts par les tests détectés par PIT :
ils se trouvent dans `hasComponent`, `hasImplementationsOf`,
`isWireInstantiable` et `getContextKey`. Ils ne sont pas comptabilisés comme
des survivants exécutés : leurs instructions mutées n’ont pas été atteintes.

Le rapport est généré sous `tika-serialization/target/pit-reports/`. Pour
reproduire une mesure centrée sur la classe choisie :

```powershell
.\mvnw.cmd -pl tika-serialization org.pitest:pitest-maven:1.30.0:mutationCoverage "-DtargetClasses=org.apache.tika.serialization.ComponentNameResolver" "-DtargetTests=org.apache.tika.serialization.*Test"
```

## Configuration ChatUniTest et modèle local

Le plugin ChatUniTest 2.1.1 est configuré dans
`tika-serialization/pom.xml`.

Les répertoires configurés pour les données temporaires et les tests générés
sont `tika-serialization/target/chatunitest-info` et
`tika-serialization/target/chatunitest-generated`.

Pour générer des tests de la classe sélectionnée, Ollama doit être lancé et
l’alias local doit être disponible. Les commandes prévues sont :

```powershell
ollama list
.\mvnw.cmd -pl tika-serialization -am install "-DskipTests"
.\mvnw.cmd -f tika-serialization/pom.xml chatunitest:class "-DselectClass=org.apache.tika.serialization.ComponentNameResolver"
```

## Résultats de génération et évaluation des tests

### Retour d'expérience avec ChatUniTest

J'ai fini par obtenir un résultat utilisable, mais il a fallu pas mal
d'essais pour arriver à des tests qui compilent et ne perturbent pas les
autres tests du module.

**Environnement :** ChatUniTest Maven plugin 2.1.1, Java 25, Maven 3.9.12
(wrapper), Ollama en local, Ryzen 9 7940HS, 16 Go de RAM et RTX 4060 Laptop
avec 8 Go de VRAM.

#### Résultat final

ChatUniTest a généré 77 cas de test, couvrant 16 des 17 méthodes publiques de
`ComponentNameResolver`. Ils sont regroupés dans
`ComponentNameResolverTest.java`, à la suite du test écrit à la main : chaque
classe générée y est recopiée telle quelle sous forme de classe interne JUnit 5
`@Nested` (même nom, mêmes champs, même méthode `setUp`, mêmes tests). Les
seules modifications sont structurelles, les imports sont remontés en tête du
fichier et `public class X` est devenu `@Nested class X`. Une fusion
aurait obligé à renommer des champs (`mockRegistry1`, `mockRegistry2`) et des
méthodes `setUp` présents dans plusieurs classes, donc à modifier le code
généré. Les seuls retraits ont porté sur des méthodes de test complètes qui
échouaient ou dépendaient de l'ordre d'exécution, et Spotless a fait le
reformatage.

La commande `.\mvnw.cmd -pl tika-serialization test` a terminé avec succès :
293 tests, 0 échec, 0 erreur et 2 tests ignorés. Les deux tests ignorés sont
des cas existants de `ExternalParserTest` qui nécessitent des outils externes.
J'ai aussi vérifié l'ordre d'exécution : chaque classe générée a été exécutée
avant et après les tests existants, puis les tests ont été lancés dans
plusieurs ordres aléatoires, à partir d'une copie figée des fichiers de build.
Aucun échec n'est apparu.

| Méthode(s) | Cas générés conservés | Emplacement actuel |
| --- | ---: | --- |
| `registerRegistry`, `resolveClass`, `getFriendlyName`, `hasComponent`, `getComponentInfo` | 10 | `ComponentNameResolverTest.java` |
| `isWireInstantiable` | 12 | `ComponentNameResolverTest.java` |
| `isWireBlocked` | 13 | `ComponentNameResolverTest.java` |
| `findContextKeyInterface` | 13 | `ComponentNameResolverTest.java` |
| `usesCompactFormat` | 13 | `ComponentNameResolverTest.java` |
| `determineContextKey` | 4 | `ComponentNameResolverTest.java` |
| `getContextKey` | 5 | `ComponentNameResolverTest.java` |
| `hasImplementationsOf` | 2 | `ComponentNameResolverTest.java` |
| `registerComponentConfig` | 2 | `ComponentNameResolverTest.java` |
| `getContextKeyInterfaces`, `getWireInstantiableContextKeys`, `getWireBlockedContextKeys` | 1 chacune | `ComponentNameResolverTest.java` |
| `getComponentConfig` | 0 | Générés, mais écartés : ils perturbaient les tests existants ou dépendaient de l'ordre |

Les accesseurs simples ne sont pas tous sélectionnés pendant une génération
complète. J'ai donc relancé certaines méthodes avec l'objectif ciblé du plugin
et `-DselectMethod=ComponentNameResolver#<méthode>`.

#### Tests générés écartés

J'ai supprimé 13 méthodes de test complètes et écarté une classe entière,
après avoir reproduit les problèmes :

| Tests retirés | Pourquoi |
| --- | --- |
| `registerRegistry_0_0`: `testRegisterRegistry`, `testRegisterRegistryWithExistingIndex` | L'assertion confondait le nom d'index avec un nom de composant; `hasComponent(indexName)` ne vérifie pas l'existence d'un registre. |
| `hasComponent_4_0`: deux tests du cas où le composant n'existe pas | Assertions incorrectes, conservées par le plugin parce que seuls les échecs d'assertion ne font pas échouer sa génération. |
| `getComponentInfo_5_0`: deux tests | Un test avait une assertion incorrecte; l'autre réutilisait un nom laissé dans le registre statique par un autre test et échouait selon l'ordre d'exécution. |
| `hasImplementationsOf_6_0`: deux tests | L'un avait une assertion incorrecte; l'autre supposait qu'aucun composant réel n'implémente `Translator`, ce qui n'est pas vrai après le chargement des registres de Tika. |
| `registerComponentConfig_7_0`: trois tests | Ils remplaçaient dans la map statique les configurations réelles de `Parser` et `Detector` par des fausses configurations, sans les restaurer; jusqu'à 23 tests existants pouvaient alors échouer. |
| `getComponentConfig_8_0`: classe entière, deux tests | Le test d'un composant enregistré avait le même effet destructeur sur les configurations statiques; celui d'un composant non enregistré ne passait que si Tika n'avait pas encore initialisé son registre. |

ChatUniTest marque parfois une classe comme réussie même si certaines
assertions échouent. Il a lui-même retiré des méthodes non compilables de
`resolveClass` et `getFriendlyName`, mais les échecs d'assertion ont dû être
repérés en exécutant les tests séparément.

#### Problèmes rencontrés et solutions

**Ollama et le modèle.** Ollama tournait d'abord sur le CPU avec un contexte
de 4096 tokens, ce qui tronquait les prompts. J'ai créé l'alias `code-llama`
avec un contexte de 8192 tokens, car le plugin n'accepte qu'une liste fixe de
noms de modèles. La RTX 4060 n'avait pas été détectée au démarrage d'Ollama;
un redémarrage propre a réglé le problème. Le modèle 7B ne corrigeait pas ses
erreurs; le modèle 14B fonctionnait mieux, mais manquait de mémoire et
utilisait le disque. J'ai défini `OLLAMA_FLASH_ATTENTION=1`,
`OLLAMA_KV_CACHE_TYPE=q8_0` et `num_gpu 38` (38 couches sur 49 sur le GPU).

**Plugin et build.** J'ai augmenté les limites de réparation à cinq tours et
réglé `maxPromptTokens`, `maxResponseTokens`, `temperature` à 0.2 et
`dependencyDepth` à 1. Le Byte Buddy 1.10.20 amené par
`chatunitest-starter:1.4.0` empêchait Mockito de créer des mocks sur Java 25;
j'ai épinglé `byte-buddy` et `byte-buddy-agent` à la version 1.17.7. Le
starter amenait aussi JUnit 4, ce qui faisait choisir à Surefire le mauvais
fournisseur : j'ai exclu `junit-vintage-engine`, `junit-platform-runner` et
`junit:junit`. Checkstyle rejetait les imports générés (imports génériques et
inutilisés); `ComponentNameResolverTest.java`, qui contient les classes
générées avec leurs imports d'origine, est donc exclu de Checkstyle, mais reste
formaté par Spotless. Enfin, l'objectif de copie du plugin remplace tout
`src/test`; j'ai copié uniquement les fichiers de tests voulus.

**Prompts.** Les prompts par défaut encourageaient la réflexion et Mockito 3.
Je les ai adaptés pour imposer l'API publique, Mockito 5, les mocks des
collaborateurs, `doReturn` pour les méthodes qui renvoient `Class<?>` et les
imports explicites. Le contexte ne montrait que 4 méthodes sur 17 et le
modèle inventait des appels. J'ai donc ajouté l'API publique complète, une
règle de n'utiliser que les méthodes documentées et une référence générée
avec `javap` par `gen_api_ref.py`. J'ai aussi limité chaque génération à la
méthode ciblée, masqué les champs privés et inclus dans les prompts les blocs
`static { ... }` qui initialisent les listes d'interfaces.

#### Fichiers et réglages

Les changements de configuration sont dans `tika-serialization/pom.xml` et
les prompts personnalisés, le script `gen_api_ref.py` et
`api/ComponentNameResolver.txt` sont dans
`tika-serialization/chatunitest-prompts/`. Les tests sont dans
`tika-serialization/src/test/java/org/apache/tika/serialization/ComponentNameResolverTest.java`. Hors du
dépôt, l'alias Ollama et les variables d'environnement
`OLLAMA_FLASH_ATTENTION` et `OLLAMA_KV_CACHE_TYPE` restent propres à la
machine utilisée.

### Oracles générés et oracle écrit à la main

Le test écrit à la main, `unregisteredComponentGivesActionableMessage`,
vérifie un comportement visible par l'utilisateur : quand un composant est
inconnu, le message de l'exception doit contenir le nom demandé, les deux
causes probables (faute de frappe ou module absent du classpath), l'exemple du
module `tika-parser-tess4j-module` et la règle de sécurité. Son oracle vient
d'une exigence (TIKA-4750), pas du code soit un seul test, mais une intention
claire.

Les oracles générés sont surtout des comparaisons de valeurs de retour :

| Catégorie | Méthodes | Appréciation |
| --- | --- | --- |
| Énumération des listes statiques | `isWireInstantiable`, `isWireBlocked`, `findContextKeyInterface`, `usesCompactFormat` et les trois accesseurs | Un test par interface, avec la valeur attendue recopiée du bloc `static { ... }`. Oracles spécifiques et efficaces contre les mutants booléens, mais très redondants (12 ou 13 tests par méthode au lieu d'un test paramétré) et calqués sur l'implémentation : ils détectent un changement de la liste, pas une erreur de logique. Aucun ne vérifie la propriété de sécurité attendue (la liste autorisée et la liste bloquée couvrent ensemble toutes les interfaces, sans recouvrement). |
| Scénarios avec registres simulés | `resolveClass`, `getComponentInfo`, `getContextKey`, `hasComponent`, `hasImplementationsOf` | Scénarios pertinents (premier ou deuxième registre, composant absent, exception avalée), avec des mocks Mockito. Plusieurs faiblesses toutefois : les tests de `getContextKey` utilisent la même classe comme composant et comme clé de contexte (`new ComponentInfo(testClass, true, testClass)`), si bien qu'une erreur qui renverrait la classe du composant au lieu de la clé passerait inaperçue; `hasComponent` et `hasImplementationsOf` ne vérifient que le cas positif, les cas négatifs générés étant faux ou dépendants de l'ordre; `hasImplementationsOf(Object.class)` est vrai pour n'importe quel composant, ce qui en fait une vérification triviale. |
| Choix de clé | `determineContextKey` | Le meilleur exemple : des valeurs distinctes (`Object` comme composant, `Parser` comme clé) et les trois branches de la méthode couvertes. |
| Vérifications hors sujet | `registerRegistry`, `registerComponentConfig`, `getFriendlyName` | Les tests conservés vérifient qu'un argument `null` lève une `NullPointerException`, ce qui teste surtout `ConcurrentHashMap` et non l'enregistrement; pour `getFriendlyName`, seul le cas « aucun nom trouvé » reste. |

Aucun test généré ne vérifie le contenu du message d'erreur produit par
`unregisteredMessage`. En résumé, l'IA produit beaucoup de tests proches de
l'implémentation, qui couvrent largement la classe, mais vérifient peu
l'intention; le test écrit à la main est unique, mais vérifie un comportement
attendu par l'utilisateur.

## Analyse de mutation avec les tests générés

### Méthode

Les trois mesures ont été faites dans le même environnement : d'abord avec la
version d'origine de `ComponentNameResolverTest` (commit `ffd712980`), puis
avec les tests générés ChatUniTest, puis avec les cinq tests manuels
`manual…` en plus (mesure finale du **2026-10-12**).

```powershell
.\mvnw.cmd -pl tika-serialization clean test
.\mvnw.cmd -pl tika-serialization org.pitest:pitest-maven:1.30.0:mutationCoverage "-DoutputFormats=XML,HTML" "-DtimestampedReports=false"
```

La portée de PIT est celle de `tika-serialization/pom.xml`
(`org.apache.tika.serialization.*` et `org.apache.tika.config.loader.*`, tous
les tests du module). Les valeurs `-DtargetClasses` passées en ligne de
commande ne remplacent pas celles du POM : la commande de la section
« Couverture et mutants avec les tests existants » mesure donc la même portée.
Les résultats ci-dessous sont filtrés sur `ComponentNameResolver`, à partir de
`target/site/jacoco/jacoco.csv` et de `target/pit-reports/mutations.xml`.

**Commit de la dernière mesure JaCoCo et PIT** (colonnes « Avec les tests
générés » et « + tests manuels ») : [`8516c8b76`](https://github.com/Chrisamy/tika/commit/8516c8b76)
sur le fork `Chrisamy/tika`, **2026-10-12**, après `clean test`, PIT comme ci-dessus,
puis `jacoco:report` pour régénérer `jacoco.csv`. À ce commit, PIT filtré sur
`ComponentNameResolver` rapporte **38 / 39** mutants tués (**97 %**) ; JaCoCo
indique **117 / 117** lignes et **50 / 50** branches couvertes pour cette classe.
La colonne « Tests d'origine » reste ancrée au commit `ffd712980` (état initial de
`ComponentNameResolverTest`).

### Résultats

| Mesure | Tests d'origine | Avec les tests générés | + tests manuels |
| --- | ---: | ---: | ---: |
| Tests du module (tous réussis) | 216 | 293 | 298 |
| Lignes couvertes (JaCoCo) | 91 / 117 (78 %) | 111 / 117 (95 %) | 117 / 117 (100 %) |
| Branches couvertes (JaCoCo) | 34 / 50 (68 %) | 46 / 50 (92 %) | 50 / 50 (100 %) |
| Méthodes couvertes (JaCoCo) | 15 / 19 | 19 / 19 | 19 / 19 |
| Mutants tués | 19 / 39 | 31 / 39 | 38 / 39 |
| Mutants survivants | 10 | 6 | 1 |
| Mutants sans couverture | 10 | 2 | 0 |
| **Score de mutation** | **49 %** | **79 %** | **97 %** |

La mesure PIT d'origine est identique à celle de la section initiale (mêmes 39
mutants). Les colonnes « générés » et « manuels » proviennent du pipeline
`clean test` puis PIT sur tout le module `tika-serialization` (portée du
`pom.xml`), chiffres filtrés sur `ComponentNameResolver` dans
`target/pit-reports/mutations.xml` et `target/site/jacoco/jacoco.csv`.

Les tests générés laissent 8 mutants non tués (6 survivants, 2 sans
couverture). Les tests manuels en tuent **7 de plus** ; il reste **un**
mutant survivant (limite de condition sur les séparateurs de
`unregisteredMessage`, voir ci-dessous).

### Mutants détectés grâce aux tests générés

Les tests générés tuent 12 mutants qui survivaient ou n'étaient pas couverts
avec les tests d'origine. Ces derniers n'atteignaient ces méthodes
qu'indirectement (chargement de configuration par `TikaLoader`, sérialisation
de `ParseContext`), sans vérifier leur valeur de retour, ou pas du tout. Les
tests générés appellent chaque méthode directement et comparent le résultat
exact : toute mutation qui change la valeur renvoyée est détectée.

| Ligne | Méthode | Mutation | Avant | Test qui le tue | Pourquoi |
| ---: | --- | --- | --- | --- | --- |
| 210 | `getFriendlyName` | retourne `""` au lieu de `null` | survivant | `getFriendlyName_3_0.testGetFriendlyName_NoFriendlyNameInAnyRegistry` | Aucun registre ne connaît la classe; le test attend `null`, le mutant renvoie `""`. |
| 222 | `hasComponent` | retourne `false` | sans couverture | `hasComponent_4_0.testHasComponent_WhenComponentExists_ReturnsTrue` | Le registre simulé contient le composant; `assertTrue` échoue si la méthode renvoie `false`. |
| 259 | `hasImplementationsOf` | condition inversée | sans couverture | `hasImplementationsOf_6_0.testHasImplementationsOf_withAbstractType` | Avec `Object.class`, tous les composants sont compatibles; la condition inversée n'est jamais vraie, la méthode renvoie `false` au lieu de `true`. |
| 260 | `hasImplementationsOf` | retourne `false` | sans couverture | `hasImplementationsOf_6_0.testHasImplementationsOf_withMatchingComponent` | Un composant `Parser` est enregistré; le test attend `true`. |
| 307 | `isWireInstantiable` | retourne `false` | sans couverture | `isWireInstantiable_11_0.testIsWireInstantiable_WithContentHandlerFactory` | `ContentHandlerFactory` est dans la liste autorisée; le test attend `true`. |
| 307 | `isWireInstantiable` | retourne `true` | sans couverture | `isWireInstantiable_11_0.testIsWireInstantiable_WithTranslator` | `Translator` est bloqué; le test attend `false`. |
| 336 | `findContextKeyInterface` | retourne `null` | survivant | `findContextKeyInterface_14_0.testFindContextKeyInterface_withContentHandlerFactory` | Le test attend l'interface correspondante, pas `null`. |
| 350 | `usesCompactFormat` | retourne `true` | survivant | `usesCompactFormat_15_0.testUsesCompactFormat_WithNonContextKeyInterface` | Un type ordinaire doit donner `false`. |
| 350 | `usesCompactFormat` | condition inversée | survivant | `usesCompactFormat_15_0.testUsesCompactFormat_WithTranslator` | `Translator` est une clé de contexte; le test attend `true`. |
| 372 | `determineContextKey` | retourne `null` | survivant | `determineContextKey_16_0.testDetermineContextKey_withInterfaceKeyFound` | Sans clé explicite, `Detector` doit être retenu comme interface; le test compare la clé exacte. |
| 387 | `getContextKey` | condition inversée | sans couverture | `getContextKey_17_0.testGetContextKey_FriendlyNameFoundInSecondRegistry` | Le premier registre ne connaît pas la classe : la condition inversée lit un nom `null` et ne renvoie pas la clé du second registre. |
| 390 | `getContextKey` | retourne `null` | sans couverture | `getContextKey_17_0.testGetContextKey_FriendlyNameFoundInSecondRegistry` | Le test attend la clé `Detector`, pas `null`. |

Les noms de tests sont abrégés : `getFriendlyName_3_0` désigne la classe
imbriquée `ComponentNameResolverTest.ComponentNameResolver_getFriendlyName_3_0_Test`.
Les mutants déjà tués par les tests d'origine (par exemple dans `resolveClass`,
`isWireBlocked` ou `determineContextKey`) le sont toujours; PIT les attribue
maintenant souvent à un test généré, parce qu'il retient le premier test qui
tue le mutant.

### Mutants détectés grâce aux tests manuels

Les tests `manual…` tuent **8** mutants que PIT attribue explicitement à ces
méthodes (7 étaient encore vivants ou sans couverture avec les tests générés
seuls). Le bloc `catch` de `resolveClass` (lignes 146–148) est couvert par
`manualResolveClassContinuesAfterTikaConfigExceptionInEarlierRegistry`, mais PIT
ne génère pas de mutant sur ce `catch` vide.

| Ligne | Méthode | Mutation | Avant (générés) | Test manuel | Pourquoi |
| ---: | --- | --- | --- | --- | --- |
| 173 | `unregisteredMessage` | condition inversée | survivant | `manualUnregisteredMessageWhenNoComponentsRegistered` | Registres vidés : le test exige le texte « No components are currently registered »; le mutant prend la branche « liste des composants ». |
| 181 | `unregisteredMessage` | condition inversée | survivant | `manualUnregisteredMessageListsFiftyNamesThenEllipsisForFiftyOneComponents` | 51 noms : le mutant coupe la liste au premier nom; le test vérifie le suffixe `", ..."` et l'absence du 51e nom. |
| 185 | `unregisteredMessage` | condition inversée | survivant | `manualUnregisteredMessageListsFiftyNamesThenEllipsisForFiftyOneComponents` | Le mutant supprime les séparateurs `", "`; le test contrôle la chaîne `manual-comp-01, manual-comp-02`. |
| 189 | `unregisteredMessage` | `shown++` → `shown--` | survivant | `manualUnregisteredMessageListsFiftyNamesThenEllipsisForFiftyOneComponents` | Le mutant fausse le compteur au-delà de la limite 50; le test impose 50 noms visibles puis `", ..."`. |
| 194 | `unregisteredMessage` | retourne `""` | déjà tué (générés) | `manualUnregisteredMessageWhenNoComponentsRegistered` | PIT crédite aussi le test manuel ; la mutation était déjà couverte avant les `manual…`. |
| 221 | `hasComponent` | condition inversée | survivant | `manualHasComponentReturnsFalseWhenNoRegistryHasName` | Deux registres mockés sans le nom : le mutant renvoie `true` trop tôt; `assertFalse` échoue. |
| 225 | `hasComponent` | retourne `true` | sans couverture | `manualHasComponentReturnsFalseWhenNoRegistryHasName` | Même scénario : seul un oracle `false` tue ce mutant. |
| 264 | `hasImplementationsOf` | retourne `true` | sans couverture | `manualHasImplementationsOfReturnsFalseWhenNoMatchingType` | Registre avec `String` seulement, requête `Parser.class` : le test attend `false`. |

### Mutants non détectés (après tests manuels)

| Ligne | Méthode | Mutation | Statut | Raison |
| ---: | --- | --- | --- | --- |
| 185 | `unregisteredMessage` | *changed conditional boundary* (`shown > 0`) | survivant | PIT remplace `>` par `>=`. Avec plusieurs noms, le message reste souvent identique au mutant sur la troncature à 50 entrées ; aucun oracle ne distingue ce cas du comportement nominal. Un test ciblant un seul nom enregistré et l'absence de virgule en tête pourrait le tuer ; à documenter comme quasi équivalent si on ne l'ajoute pas. |

Avec les tests manuels, JaCoCo indique **100 %** des lignes et branches de
`ComponentNameResolver`, y compris la branche `catch` de `resolveClass`.


## Tests manuels supplémentaires

Les tests ci-dessous sont des méthodes `@Test` dans `ComponentNameResolverTest`
(écrites à la main, placées avant les classes `@Nested` générées par ChatUniTest).
Quand il faut contrôler la liste des composants connus, le test sauvegarde le
champ statique `REGISTRIES` par réflexion, vide la carte, puis la restaure dans
un bloc `finally`.

### `manualHasComponentReturnsFalseWhenNoRegistryHasName`

- **Nom du test** : `manualHasComponentReturnsFalseWhenNoRegistryHasName`
- **Intention** : lorsque aucun registre ne déclare le nom demandé, `hasComponent`
  doit renvoyer `false` (et non `true` par défaut ou par condition inversée).
- **Motivation des données de test** : deux registres mockés distincts et un nom
  de composant unique (`manual-missing-component-xyz`) ; les deux mocks répondent
  `false` à `hasComponent`, ce qui force l’exécution de la branche finale de
  la méthode.
- **Oracle** : `assertFalse(hasComponent(nom))` ; le comportement attendu est la
  définition de `hasComponent` (parcours des registres, retour `false` si aucun
  ne contient le nom).

### `manualHasImplementationsOfReturnsFalseWhenNoMatchingType`

- **Nom du test** : `manualHasImplementationsOfReturnsFalseWhenNoMatchingType`
- **Intention** : `hasImplementationsOf` renvoie `false` lorsqu’aucun composant
  enregistré n’est assignable au type abstrait demandé.
- **Motivation des données de test** : un seul registre mocké dont la carte ne
  contient qu’un composant `String.class` ; on interroge `Parser.class`, type
  sans implémentation enregistrée dans ce registre isolé.
- **Oracle** : `assertFalse(hasImplementationsOf(Parser.class))` ; aligné sur la
  boucle de `hasImplementationsOf` qui ne trouve aucun `ComponentInfo` assignable.

### `manualResolveClassContinuesAfterTikaConfigExceptionInEarlierRegistry`

- **Nom du test** : `manualResolveClassContinuesAfterTikaConfigExceptionInEarlierRegistry`
- **Intention** : si un registre connaît le nom mais `getComponentClass` lève
  `TikaConfigException`, la résolution doit continuer avec les autres registres.
- **Motivation des données de test** : registre « échec » avec `hasComponent → true`
  et `getComponentClass` qui lève ; registre « succès » avec la même clé et
  `Detector.class` ; nom unique `manual-resolve-fallback`.
- **Oracle** : `assertEquals(Detector.class, resolveClass(nom, classLoader))` ; la
  spécification du `catch` dans `resolveClass` (ignorer l’exception et poursuivre).

### `manualUnregisteredMessageListsFiftyNamesThenEllipsisForFiftyOneComponents`

- **Nom du test** : `manualUnregisteredMessageListsFiftyNamesThenEllipsisForFiftyOneComponents`
- **Intention** : le message d’erreur pour un nom inconnu liste les composants
  connus (séparateurs `", "`, au plus 50 noms affichés, puis `", ..."`).
- **Motivation des données de test** : **51** noms contrôlés `manual-comp-01` …
  `manual-comp-51` dans `getAllComponents().keySet()` d’un registre mocké isolé ;
  résolution d’un nom absent (`manual-unknown-for-list`).
- **Oracle** : le message contient `51 registered component(s):`, une sous-chaîne
  avec les premiers noms triés (`manual-comp-01, manual-comp-02`), le
  cinquantième nom, le suffixe `", ..."`, et **pas** `manual-comp-51` (51e entrée
  omise par la limite).

### `manualUnregisteredMessageWhenNoComponentsRegistered`

- **Nom du test** : `manualUnregisteredMessageWhenNoComponentsRegistered`
- **Intention** : sans aucun composant enregistré, le message signale explicitement
  l’absence de fichiers `.idx` sur le classpath.
- **Motivation des données de test** : `REGISTRIES` vidé dans `@BeforeEach` de
  `ManualTests` (stratégie **A** : réflexion + sauvegarde/restauration), puis
  appel à `resolveClass` sur un nom inconnu.
- **Oracle** : le message contient `No components are currently registered` et la
  mention `META-INF/tika/*.idx`, comme dans le code de `unregisteredMessage` lorsque
  `known.isEmpty()`.

## Exécution dans l’intégration continue

### Exécution locale

Les tests de la tâche 2 (classe unique `ComponentNameResolverTest` : test
initial, cas générés ChatUniTest, cinq tests manuels `manual…`) passent en
local avec la même commande que la CI :

```powershell
.\mvnw.cmd -pl tika-serialization -am install -DskipTests
.\mvnw.cmd -pl tika-serialization test -Dtest=ComponentNameResolverTest
```

Dernière vérification : **2026-10-12** (Windows, JDK 17).

### GitHub Actions (fork `Chrisamy/tika`)

| Élément | Détail |
| --- | --- |
| Branche | `main` |
| Commit poussé | [`4be6d89ca`](https://github.com/Chrisamy/tika/commit/4be6d89ca) — documentation mutation, tests manuels |
| Workflow **tâche 2** (preuve CI) | [`tache2 tika-serialization tests`](https://github.com/Chrisamy/tika/actions/workflows/tache2-tika-serialization.yml) — compile les dépendances (`-am install -DskipTests`), puis **seulement** `-Dtest=ComponentNameResolverTest` |
| Workflows **upstream** (hérités Tika) | p. ex. [`main jdk17 build`](https://github.com/Chrisamy/tika/actions/workflows/main-jdk17-build.yml) — suite Maven **complète** du projet ; **non** utilisés comme preuve de la tâche 2 |
| Tableau des exécutions | [Actions — tous les workflows](https://github.com/Chrisamy/tika/actions) |

**Activation (une fois sur un fork)** : tant que le propriétaire du fork n’a pas
accepté l’exécution des workflows (onglet **Actions** → *I understand my
workflows, go ahead and enable them*), GitHub n’enregistre **aucun** run après
un `git push`. Après activation, un nouveau push sur `main` ou un lancement
manuel (*Run workflow*) sur **tache2 tika-serialization tests** déclenche la CI.

**Exécution réussie à lier ici** (à mettre à jour dès qu’un run est vert) :

- Run **tache2 tika-serialization tests** : [run réussi du 2026-10-12](https://github.com/Chrisamy/tika/actions/runs/38312448477) (job `test`, ~1 min 16 s)
- Résumé attendu : job `test` réussi ; uniquement la classe
  `ComponentNameResolverTest` (tests manuels + `@Nested` ChatUniTest).

## Documentation générale du projet Apache Tika

Welcome to Apache Tika  <https://tika.apache.org/>
=================================================

[![license](https://img.shields.io/github/license/apache/tika.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0)
[![Jenkins](https://img.shields.io/jenkins/s/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk17.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk17/)
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk17.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk17/lastBuild/testReport/)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.tika/tika.svg?maxAge=86400)](http://search.maven.org/#search|ga|1|g%3A%22org.apache.tika%22)

Apache Tika(TM) is a toolkit for detecting and extracting metadata and structured text content from various documents using existing parser libraries.

Tika is a project of the [Apache Software Foundation](https://www.apache.org).

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika project logo are trademarks of The Apache Software Foundation.

Quick Start
===========

**Parse a file in Java:**

```java
import org.apache.tika.Tika;

Tika tika = new Tika();
String text = tika.parseToString(new File("document.pdf"));
System.out.println(text);
```

**From the command line:**

```bash
java -jar tika-app-*.jar --text document.pdf
```

**Maven dependency:**

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>4.x.y</version>
    <type>pom</type>
</dependency>
```

Getting Started
===============
Pre-built binaries of Apache Tika standalone applications are available
from https://tika.apache.org/download.html . Pre-built binaries of all the
Tika jars can be fetched from Maven Central or your favourite Maven mirror.

**Tika 2.X and support for Java 8 reached End of Life (EOL) in April, 2025. 
See [Tika Roadmap 2.x, 3.x and beyond](https://cwiki.apache.org/confluence/display/TIKA/Tika+Roadmap+--+2.x%2C+3.x+and+Beyond).** 

Tika is based on **Java 17** and uses the [Maven 3](https://maven.apache.org) build system.
**N.B.** [Docker](https://www.docker.com/products/personal) is used for tests in tika-integration-tests. If Docker is not installed, those tests are skipped.

To build Tika from source, use the following command in the main directory:

    ./mvnw clean install

The Maven wrapper (`mvnw`) is included in the repository and will automatically download
the correct Maven version if needed. On Windows, use `mvnw.cmd` instead.

The build consists of a number of components, including a standalone runnable jar that you can use to try out Tika features. You can run it like this:

    java -jar tika-app/target/tika-app-*.jar --help


To build a specific project (for example, tika-server-standard):

    ./mvnw clean install -am -pl :tika-server-standard

If the ossindex-maven-plugin is causing the build to fail because a dependency
has now been discovered to have a vulnerability:

    ./mvnw clean install -Dossindex.skip


Faster Builds
=============

**Fast profile** - Use `-Pfast` to skip tests, checkstyle, and spotless:

    ./mvnw clean install -Pfast

**Parallel builds** - Add `-T1C` to build with 1 thread per CPU core:

    ./mvnw clean install -Pfast -T1C

**Maven Daemon (mvnd)** - Keeps a warm JVM running for 2-3x faster rebuilds:

```bash
# Install: https://github.com/apache/maven-mvnd
# macOS: brew install mvndaemon/tap/mvnd

# Use exactly like mvn
mvnd clean install -Pfast
mvnd test -pl :tika-core
```

**Combine both** for maximum speed during development:

    mvnd clean install -Pfast -T1C


Reproducible Builds
===================

Apache Tika supports [reproducible builds](https://reproducible-builds.org/). This means
that building the same source code with the same JDK version should produce
byte-for-byte identical artifacts, regardless of the build machine or time.

Key configuration:
- `project.build.outputTimestamp` is set in `tika-parent/pom.xml`
- All Maven plugins are configured to produce deterministic output

To verify the build plan supports reproducibility:

    ./mvnw artifact:check-buildplan

To verify two builds produce identical artifacts:

    ./mvnw clean install -DskipTests
    mv ~/.m2/repository/org/apache/tika tika-build-1
    ./mvnw clean install -DskipTests
    diff -r tika-build-1 ~/.m2/repository/org/apache/tika


Maven Dependencies
==================

Apache Tika provides *Bill of Material* (BOM) artifact to align Tika module versions and simplify version management. 
To avoid convergence errors in your own project, import this
bom or Tika's parent pom.xml in your dependency management section.

If you use Apache Maven:

```xml
<project>
  <dependencyManagement>
    <dependencies>
      <dependency>
       <groupId>org.apache.tika</groupId>
       <artifactId>tika-bom</artifactId>
       <version>4.x.y</version>
       <type>pom</type>
       <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-parsers-standard-package</artifactId>
      <type>pom</type>
      <!-- version not required since BOM included -->
    </dependency>
  </dependencies>
</project>
```

For Gradle:

```kotlin
dependencies {
  implementation(platform("org.apache.tika:tika-bom:4.x.y"))

  // version not required since bom (platform in Gradle terms)
  implementation("org.apache.tika:tika-parsers-standard-package@pom")
}
```

Migrating to 4.x
================
TBD

Contributing
============
See [CONTRIBUTING.md](CONTRIBUTING.md) and https://tika.apache.org/contribute.html

[![contributors](https://contributors-img.web.app/image?repo=apache/tika)](https://github.com/apache/tika/graphs/contributors)

Building from a Specific Tag
============================
Let's assume that you want to build the 3.0.1 tag:
```
0. Download and install hub.github.com
1. git clone https://github.com/apache/tika.git
2. cd tika
3. git checkout 3.0.1
4. ./mvnw clean install
```

If a new vulnerability has been discovered between the date of the
tag and the date you are building the tag, you may need to build with:

```
4. ./mvnw clean install -Dossindex.skip
```

If a local test is not working in your environment, please notify
 the project at dev@tika.apache.org. As an immediate workaround,
 you can turn off individual tests with e.g.:

```
4. ./mvnw clean install -Dossindex.skip -Dtest=\!UnpackerResourceTest#testPDFImages
```

License (see also LICENSE.txt)
==============================

Collective work: Copyright 2011 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.

Apache Tika includes a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the licenses listed in the LICENSE.txt file.

Export Control
==============

This distribution includes cryptographic software.  The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.  BEFORE using any encryption software, please  check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to  see if this is permitted.  See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.  The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

Apache Tika uses the Bouncy Castle generic encryption libraries for extracting text content and metadata from encrypted PDF files.  See <http://www.bouncycastle.org/> for more details on Bouncy Castle.  

Mailing Lists
=============

* user@tika.apache.org - About using Tika
* dev@tika.apache.org - About developing Tika

Subscribe by sending a message to `{list}-subscribe@tika.apache.org`.

Issue Tracker
=============

https://issues.apache.org/jira/browse/TIKA

Security
========

See [SECURITY.md](SECURITY.md) and https://tika.apache.org/security.html
