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

J'ai passé pas mal de temps à faire fonctionner ChatUniTest sur
`ComponentNameResolver`. Les difficultés venaient autant de l'environnement
que du plugin et des tests générés. Je garde ici les problèmes rencontrés et
ce qui a fini par aider; ce n'est pas encore un résultat entièrement terminé.

**Environnement utilisé :** ChatUniTest Maven plugin 2.1.1, Java 25, Maven
3.9.12 (wrapper), Ollama en local, ordinateur portable avec Ryzen 9 7940HS,
16 Go de RAM et RTX 4060 Laptop avec 8 Go de VRAM.

#### Ollama et choix du modèle

| Problème | Cause trouvée | Solution ou résultat |
| --- | --- | --- |
| Génération très lente et prompts tronqués | Ollama tournait sur le CPU et utilisait son contexte par défaut de 4096 tokens; les longs prompts de réparation étaient coupés. | J'ai créé un alias de modèle Ollama avec `num_ctx 8192` et `temperature 0.2`. |
| Le modèle 7B ne produisait jamais de test compilable | `qwen2.5-coder:7b` répétait les mêmes erreurs de réparation : constructeur ou champs privés, import manquant de `java.lang.reflect.Field`, et `thenReturn` appliqué à `Class<?>`. | Je suis passé à `qwen2.5-coder:14b`. |
| Le modèle 14B produisait presque plus rien | Il ne tient pas dans les 8 Go de VRAM. Avec une partie du modèle sur le GPU, la RAM manquait et Windows utilisait le disque (10–14 Go réservés, environ 4 Go réellement en RAM). | J'ai défini `OLLAMA_FLASH_ATTENTION=1` et `OLLAMA_KV_CACHE_TYPE=q8_0`, puis `num_gpu 38` dans l'alias. Ainsi, 38 des 49 couches (75 %) sont sur le GPU. Le débit obtenu était de 6–9 tokens/s, sans pagination disque. |

#### Configuration du plugin et dépendances

J'ai rencontré l'erreur Mockito
`Could not initialize plugin: interface org.mockito.plugins.MockMaker` dans
tous les tests. `chatunitest-starter:1.4.0` apporte Byte Buddy 1.10.20 et
Mockito 3.8. Maven choisissait cette version de Byte Buddy avant Mockito, alors
que Mockito 5.23 demande la version 1.17.7, compatible avec Java 25. J'ai
épinglé `byte-buddy` et `byte-buddy-agent` en version 1.17.7 dans la portée
`test`, puis vérifié qu'il était possible de créer un mock sur Java 25.

#### Prompts personnalisés

Les prompts intégrés ont provoqué beaucoup d'échecs. Quand `promptPath` est
défini, le plugin charge ses modèles depuis ce dossier. J'ai copié les 21
modèles d'origine dans `tika-serialization/chatunitest-prompts/`, puis adapté
`initial_system.ftl`, `initial.ftl` et `repair.ftl`.

| Problème dans les tests générés | Changement apporté aux prompts |
| --- | --- |
| Le modèle utilisait la réflexion sur des membres privés; les tests échouaient ensuite sur `setAccessible`, `InvocationTargetException` ou des imports manquants. | Les prompts d'origine recommandaient la réflexion et Mockito 3. J'ai demandé de n'utiliser que l'API publique, de ne pas accéder aux membres privés, d'utiliser Mockito 5, de simuler les collaborateurs plutôt que de les construire, d'utiliser `doReturn` pour `Class<?>`, d'éviter `MockitoExtension` (stubs stricts), de tenir compte de l'état statique partagé et d'écrire tous les imports. |
| Le modèle ne connaissait pas l'effet de certaines méthodes. | Le contexte du plugin ne listait que 4 des 17 méthodes publiques. Les prompts extraient maintenant toute l'API publique depuis le code de la classe (`file_content`) et l'incluent dans les deux prompts. |
| Le modèle inventait des méthodes, par exemple `ComponentConfig.getRegistry()`. | J'ai ajouté une règle qui limite le modèle aux méthodes listées, ainsi que `gen_api_ref.py`. Ce script utilise `javap` pour écrire les API publiques des types du projet mentionnés dans les signatures de la classe (dont `ComponentRegistry`, `ComponentInfo`, `ComponentConfig` et `ComponentConfig.Builder`) sous `api/<Classe>.txt`. Le prompt de réparation demande de supprimer un appel si l'erreur indique `cannot find symbol: method`. |
| Le modèle testait toute la classe, ce qui ramenait l'accès aux champs privés et les appels inventés comme `register...`. | J'ai précisé qu'il fallait tester uniquement la méthode ciblée et ajouté une expression régulière dans les prompts pour retirer les déclarations de champs `private ...;` de la source de cette méthode. |
| Certains messages d'erreur étaient tronqués (par exemple `...with modifiers `). | Je n'ai pas trouvé de réglage du plugin pour corriger ce comportement. |

#### Comportements du plugin observés

- Un test est considéré comme réussi si ses seules erreurs sont des échecs
  d'assertion (`AbstractRunner: isOnlyAssertionError`). Les tests en échec
  ne sont supprimés (`removeErrorTest`) que pour les autres types d'erreur.
  Les fichiers sauvegardés peuvent donc contenir des échecs : dans mon
  résultat, 5 des 16 méthodes de test échouaient.
- Les accesseurs simples sont ignorés, notamment `getContextKeyInterfaces`,
  `getWireInstantiableContextKeys` et `getWireBlockedContextKeys`. Le plugin
  n'a donc essayé que 14 des 17 méthodes publiques.

#### Résultats des essais

| Essai | Modèle et configuration | Résultat |
| --- | --- | --- |
| 1 | 7B, CPU, prompts d'origine, `maxRounds=2` | Aucun test; arrêt pendant la première méthode. |
| 2 | 7B, GPU, prompts d'origine | Aucune des 6 méthodes n'a abouti; génération interrompue. |
| 3 | 14B, prompts d'origine, avant la correction de Byte Buddy | Les tests compilaient, mais échouaient avec l'erreur MockMaker. |
| 4 | 14B, prompts d'origine | Aucun test; arrêt après 2 méthodes. |
| 5 | 14B, règles personnalisées v1 | Aucun test; méthode `getRegistry()` inventée. |
| 6 | 14B, règle « utiliser uniquement les méthodes listées » et références d'API (v2) | Aucun test; champs privés et méthodes inventées, puis arrêt. |
| 7 | 14B, prompts v3 limités à la méthode ciblée, 38 couches GPU | 5 méthodes tentées sur 14; 16 tests produits, 11 réussis et 5 en échec. |

#### Ce qu'il reste à faire

Le contenu des blocs statiques n'est pas inclus dans les prompts. Cinq
méthodes dépendent des listes d'interfaces initialisées dans `static { ... }` :
`isWireInstantiable`, `isWireBlocked`, `findContextKeyInterface`,
`usesCompactFormat` et `determineContextKey`. Il faudrait inclure ces blocs
dans les modèles et relancer la génération pour ces méthodes.

Le modèle 14B n'a pas corrigé plusieurs problèmes :

- `hasImplementationsOf` appelle `REGISTRIES.clear()` sur un champ privé.
- `registerComponentConfig` importe `ComponentConfig` depuis le mauvais
  package.
- `getComponentConfig` utilise `thenReturn(Class<?>)`.
- `getContextKey` échoue à la compilation durant les cinq tours; je n'ai pas
  encore vérifié le détail des erreurs.

Les tests qui échouent sont conservés par le plugin. Il faut donc les retirer
ou régénérer les méthodes concernées avant de copier les tests dans
`src/test/java`. Je n'ai pas encore utilisé l'objectif de copie du plugin, ni
vérifié que les tests passent avec `mvn test`, Checkstyle et Spotless.

#### Fichiers et réglages concernés

Dans le dépôt, les changements liés à cet essai sont :

- `tika-serialization/pom.xml` : configuration du plugin, versions de Byte
  Buddy, chemin des prompts et exclusion RAT.
- `tika-serialization/chatunitest-prompts/` : modèles personnalisés,
  `gen_api_ref.py` et `api/ComponentNameResolver.txt`.

Hors du dépôt, j'ai aussi créé l'alias Ollama `code-llama` (modèle
`qwen2.5-coder:14b`, `num_ctx 8192`, `num_gpu 38`, température 0.2) et défini
les variables d'environnement utilisateur `OLLAMA_FLASH_ATTENTION` et
`OLLAMA_KV_CACHE_TYPE`.


## Tests manuels supplémentaires


## Exécution dans l’intégration continue


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
