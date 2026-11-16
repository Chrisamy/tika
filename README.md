# Tâche 3 : Automatisation des tests de sécurité avec Semgrep

## Référentiel et documentation

- Référentiel du projet : [Chrisamy/tika](https://github.com/Chrisamy/tika)
- Ce document : [README.md](https://github.com/Chrisamy/tika/blob/main/README.md)

## Objectif et périmètre

Cette étude porte sur l'automatisation des tests de sécurité avec Semgrep.
Le travail de la tâche 3 est fait sur la branche tache3, créée à partir de la branche main à la fin de la tâche 2. Le point de départ est utilisé comme référence pour le «before» du rapport Semgrep.

La même version Semgrep (1.184.0) est utilisée pour le scan local «before», le scan local «after» et le job de CI. Ceci importe parce que le ruleset `p/java` ainsi que le Semgrep lui même
évolue au fil du temps: une nouvelle version pourrait rapporter des erreurs qui n'ont pas été introduit par nos modifications, ce qui modifierait la comparaison entre le «before» et le «after».

## Rapport Semgrep avant modifications

Le rapport baseline a été généré avant toute autre modification, a partir de la racine du repo:

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -w /src semgrep/semgrep:1.184.0 \
  semgrep scan --config p/java --metrics=off --json -o tache3/reports/semgrep-before.json tika-serialization
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -w /src semgrep/semgrep:1.184.0 \
  semgrep scan --config p/java --metrics=off -o tache3/reports/semgrep-before.txt tika-serialization
```

`MSYS_NO_PATHCONV=1` évite que Git Bash réécrive les chemins de fichiers Windows en chemins Unix, ce qui rendrait le rapport JSON invalide.

- le rapport JSON: `tache3/reports/semgrep-before.json`
- le rapport texte: `tache3/reports/semgrep-before.txt`
- Nombre d'erreurs: 0

Semprep a exécuté les 60 règles de sécurité de la version 1.184.0 du ruleset `p/java` sur 34 fichiers de `tika-serialization` et a trouvé 0 erreurs.

## Faire échouer le job de CI à l'encontre de nouvelles erreurs Semgrep

### Quel workflow a été modifié

Notre fork contient les workflow d'upstream de Tika, qui exécute le build Maven entier du projet, ainsi que le workflow que nous avons écrit pour la tâche 2, `tache2-tika-serialization.yml`,
qui build les dépendances de `tika-serialization` et exécute `ComponentNameResolverTest`.

Nous avons modifié `tache2-tika-serialization.yml`:

- Nous l'avons déjà utilisé pour le CI dans la tâche 2, donc il couvre un module sur lequel nous travaillons.
- Le run prends environ 1 minute, plutôt que 5 minutes pour le build Maven entier.

### Changements

**Triggers.** Le workflow s'exécute à chaque push à la branche tache3 ou main, à chaque pull request, et manuellement.
**Nouveau job Semgrep.** Le job a été ajouté juste après le job `test`:

```yaml
semgrep:
    name: Semgrep
    runs-on: ubuntu-latest
    container: semgrep/semgrep:1.184.0
    permissions:
        contents: read
    steps:
        - uses: actions/checkout@v6
          with:
              fetch-depth: 0
        - name: Fail on new Semgrep findings
          env:
              EVENT_NAME: ${{ github.event_name }}
              PR_BASE: ${{ github.event.pull_request.base.sha }}
              PUSH_BEFORE: ${{ github.event.before }}
          run: |
              git config --global --add safe.directory "$GITHUB_WORKSPACE"
              if [ "$EVENT_NAME" = "pull_request" ]; then
                BASE="$PR_BASE"
              else
                BASE="$PUSH_BEFORE"
              fi
              if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ] \
                 || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
                BASE=$(git rev-parse HEAD~1)
              fi
              echo "Baseline commit: $BASE"
              semgrep scan --config p/java --metrics=off --baseline-commit "$BASE" --error tika-serialization
```

### Analyse des changements

**Commit de baseline.** Le commit de baseline représent le code avant le dernier changement:

- le HEAD d'une branche cible pour une pull request (`github.event.pull_request.base.sha`)
- Le commit juste avant celui là pour un push (`github.event.before`)
- Ou le commit juste avant le HEAD de la branche tache3 lorsque la valeur est invalide (`HEAD~1`)

**Historique complet.** Par défaut, `actions/checkout` ne prend qu'en compte le dernier commit. `fetch-depth: 0` est utilisé pour obtenir l'historique complet.

**Périmètre du scan.** Le scan est limité à `tika-serialization` pour éviter de scanner des fichiers qui ne sont pas pertinents pour la tâche 3.

### Validation

Chaque scénario a été validé avec des commits et push individuels :

| Scénario                                                  | Résultat attendu                                                                        | Lien du run                                                                | Résultat observé                                      |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ----------------------------------------------------- |
| Commit sans changement de code                            | Le job `semgrep` passe                                                                  | https://github.com/Chrisamy/tika/actions/runs/43824502482/job/133154976357 | Le job `semgrep` passe                                |
| Commit introduisant une vulnérabilité                     | Le job `semgrep` échoue, cette erreur est dans le log                                   | https://github.com/Chrisamy/tika/actions/runs/43825462283/job/133158137456 | Le job `semgrep` échoue, cette erreur est dans le log |
| Commit avec changement sans rapport avec la vulnérabilité | Le job `semgrep` passe, car la vulnérabilité n'est pas nouvelle par rapport au baseline | https://github.com/Chrisamy/tika/actions/runs/43826237591/job/133160638704 | Le job `semgrep` passe                                |
| Commit retirant la vulnérabilité                          | Le job `semgrep` passe encore                                                           | https://github.com/Chrisamy/tika/actions/runs/43826875758/job/133162697989 | Le job `semgrep` passe                                |

## Vulnérabilités introduites

Nous avons introduit 7 vulnérabilités de types différents dans `tika-serialization/src/main/java`, chacun détecté par une règle différente du ruleset `p/java`.
Chaque vulnérabilité a son propre commit et CWE. Chaque vulnérabilité est une nouvelle méthode ajoutée à une classe existante, aucune n'est appelée donc le module ne change pas de comportement.
Après les changements, le module build et passe tous les tests.

| Type                                       | CWE | Règle de Semgrep                                        | Fichier                                            | Ligne   | Commit                                   |
| ------------------------------------------ | --- | ------------------------------------------------------- | -------------------------------------------------- | ------- | ---------------------------------------- |
| Désérialisation non sécurisée              | 502 | `jackson-unsafe-desiralization`                         | `config/loader/TikaObjectMapperFactory.java`       | 99      | 5d6458640045216d8419ca98a539313bd321d469 |
| XML external entities                      | 611 | `documentbuilderfactory-external-general-entities-true` | `config/loader/TikaLoader.java`                    | 213     | 6f4b3912a9972051309792aec7cc3435d93226ae |
| Utilisation d'un hash faible               | 328 | `use-of-md5`                                            | `config/loader/ComponentRegistry.java`             | 182     | 59995682ed65a3e66f86b1cbd623c04c899e1467 |
| Mode de chiffrement risqué                 | 327 | `use-of-aes-ecb`                                        | `serialization/config/JsonConfigHelper.java`       | 302     | 8fe01ce836c3b8ef0569bb387a9664b3d171dad2 |
| Vecteur d'initialisation statique avec CBC | 329 | `no-static-initialization-vector`                       | `serialization/serdes/ParseContextSerializer.java` | 111-114 | 9a59309d6a8f75c717c66bdc5eaba1b6b36b5d33 |
| Utilisation d'une version de TLS faible    | 326 | `weak-ssl-context`                                      | `config/loader/TikaJsonConfig.java`                | 170     | db78e8072c8bc6f856ac24df34fd601040fcbd1c |
| Transmission en texte clair                | 319 | `unencrypted-socket`                                    | `serialization/JsonMetadataList.java`              | 152     | de90b611d664a77976b2002a4f3bf507d8f1b79c |

### Désérialisation non sécurisée

`TikaObjectMapperFactory.readPolymorphicConfig(String)`

```java
mapper.enableDefaultTyping();
return mapper.readValue(json, Object.class);
```

**- Choix du fichier :** Ce fichier crée tout les `ObjectMapper` utilisés pour lire la configuration JSON de Tika, qui est polymorphique.
**- Vulnérabilité :** Jackson instancie le nom de classe dans le JSON, et le type cible `Object` permet n'importe quel type de classe. Si un attaquant contrôle le JSON, il peut faire instancier
des classes « gadgets » et aller jusqu'à exécuter du code arbitraire. Le code de Tika ne fait pas de validation de type, donc la vulnérabilité est exploitable.
**- Semgrep :** `jackson-unsafe-deserialization`, ligne 99.

### XML external entities

`TikaLoader.isLegacyXmlConfig(Path)`

```java
dbf.setFeature("http://xml.org/sax/features/external-general-entities", true);
```

**- Choix du fichier :** Ce fichier reçoit les chemins de configuration. Tika n'utilise cependant plus de configuration XML, mais des configurations JSON.
**- Vulnérabilité :** Le parseur résout les entités externes, donc un fichier XML peut lui faire lire des fichiers locaux ou contacter des URL internes.    
**- Semgrep :** `documentbuilderfactory-external-general-entities-true` at line 213 (severity ERROR).

### Utilisation d'un hash faible

`ComponentRegistry.fingerprint(URL)`

```java
java.security.MessageDigest.getInstance("MD5").digest(in.readAllBytes());
```

**- Choix du fichier:** Ce fichier fusionne les fichiers d'un certain index trouvé sur le classpath, on peut donc avoir un même fichier dans plusieurs jar.
**- Vulnérabilité:** Les collisions MD5 sont assez simples à calculer, quelqu'un pourrait ajouter un jar au classpath un forger un fichier d'index différent avec la même signature.
**- Semgrep:** `use-of-md5` at line 182.

### Mode de chiffrement risqué

`JsonConfigHelper.decryptValue(String, byte[])`

```java
cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
```

**- Choix du fichier :** Ce fichier remplace les valeurs des paramètres fictifs des modèles de configuration, il est donc logique de déchiffrer des valeurs ici.
**- Vulnérabilé :** ECB chiffre chaque bloc indépendamment et de façon déterministe, des blocs de texte clair identiques donnent des blocs chiffrés identiques.
**- Semgrep :** `use-of-aes-ecb` at line 302.

### Vecteur d'initialisation statique avec CBC

`ParseContextSerializer.encryptSensitiveValue(String, byte[])`

```java
byte[] iv = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
```

**- Choix du fichier :** Ce fichier écrit un ParseContext en JSON, qui peut transporter des secrets comme le mot de passe d'un PDF chiffré. On peut donc chiffrer avant la sérialisation.
**- Vulnérabilé :** Le vecteur d'initialisation est un constant, CBC devient alors déterministe, le m^me mot de passe donne toujours le même texte chiffré, ce qui peut être exploité.
**- Semgrep :** `no-static-initialization-vector` at lines 111–114.

### Utilisation d'une version de TLS faible

`TikaJsonConfig.buildTikaConfig(Path)`

```java
SSLContext sslContext = SSLContext.getInstance("TLSv1");
```

**- Choix du fichier :** Ce fichier contient déjà `load(Path)` et `load(InputStream)` donc il est logique d'y rajouter un `load(URL)`.
**- Vulnérabilé :** TLSv1 est obsolète et a des faiblesses bien documentées. Un attaquant intermédiaire pourrait compromiser l'intégrité, l'authenticité et la confidentialité des données.
**- Semgrep :** `weak-ssl-context` at line 170.

### Transmission en texte clair

`JsonMetadataList.sendToCollector(List<Metadata>, String, int)`

```java
try (Socket socket = new Socket(host, port));
```

**- Choix du fichier :** Ce fichier sérialise les métadonnées produites par une analyse, on peut donc y ajouter une méthode qui les envoie à un collecteur.
**- Vulnérabilé :** Les métadonnées continennent souvent des données sensibles, sur un socket non chiffré, toute personne sur le chemin réseau peut les lire et modifier.
**- Semgrep :** `unencrypted-socket` at line 152.

## Comparaison des rapports Semgrep

Les deux rapports ont été générés avec la même version Semgrep (1.184.0) et le même ruleset `p/java`.
Garder ces paramètres identiques assure que toute différence entre les rapports viennent de notre changement de code, et non de changement d'outils ou de règles.

| Rapports | Fichiers                                    | Erreurs |
| -------- | ------------------------------------------- | ------- |
| Before   | `semgrep-before.json`, `semgrep-before.txt` | 0       |
| After    | `semgrep-after.json`, `semgrep-after.txt`   | 7       |

Le rapport «before» ne contenait pas d'erreurs, la comparaison est donc directe : chaque erreur de «after» est nouvelle par définition.
La table ci-dessous liste pour chaque règle qui a détecté une erreur, le nombre d'occurences dans les deux rapports.

| Règle                                                   | CWE | Before | After |
| ------------------------------------------------------- | --- | ------ | ----- |
| `jackson-unsafe-deserialization`                        | 502 | 0      | 1     |
| `documentbuilderfactory-external-general-entities-true` | 611 | 0      | 1     |
| `use-of-md5`                                            | 328 | 0      | 1     |
| `use-of-aes-ecb`                                        | 327 | 0      | 1     |
| `no-static-initialization-vector`                       | 329 | 0      | 1     |
| `weak-ssl-context`                                      | 326 | 0      | 1     |
| `unencrypted-socket`                                    | 319 | 0      | 1     |

## Rapport d'échec de test

Quand un test échoue, le job de CI donne au développeur un lien vers les détails de l'échec.
Voici un [exemple de test qui échoue](https://github.com/Chrisamy/tika/actions/runs/43961393103): Ouvrez le résumé du job `test`pour voir le rapport.

Pour voir le rapport sans casser un test, on a ajouté une option `force_fail` au lancement manuel du workflow. Dans l'onglet Actions, choisir `tache2 tika-serialization tests`, cliquer sur « Run workflow » et cocher « Faire échouer les tests pour vérifier le rapport ». Les tests roulent normalement, puis l'étape `tests` sort avec le code 1, ce qui déclenche le rapport.
