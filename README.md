# Tâche 3 : Automatisation des tests de sécurité avec Semgrep

## Référentiel et documentation

- Référentiel du projet : [Chrisamy/tika](https://github.com/Chrisamy/tika)
- Ce document : [README.md](https://github.com/Chrisamy/tika/blob/main/README.md)

## Objectif et périmètre

Cette étude porte sur l'automatisation des tests de sécurité avec Semgrep.
Le travail de la tâche 3 est fait sur la branche tache3, créée à partir de la branche main à la fin de la tâche 2. Le point de départ est utilisé comme référence pour le «before» du rapport Semgrep.

La même version Semgrep est utilisée pour le scan local «before», le scan local «after» et le job de CI. Ceci importe parce que le ruleset `p/java` ainsi que le Semgrep lui même
évolue au fil du temps: une nouvelle version pourrait rapporter des erreurs qui n'ont pas été introduit par nos modifications, ce qui modifierait la comparaison entre le «before» et le «after».

## Rapport Semgrep avant modifications

Le rapport baseline a été généré avant toute autre modification, a partir de la racine du repo:

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -w /src semgrep/semgrep:TODO \
  semgrep scan --config p/java --metrics=off --json -o tache3/reports/semgrep-before.json tika-serialization
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -w /src semgrep/semgrep:TODO \
  semgrep scan --config p/java --metrics=off -o tache3/reports/semgrep-before.txt tika-serialization
```

(Avec MSYS_NO_PATHCONV=1 pour éviter les problèmes de chemin sur Windows.)

- le rapport JSON: `tache3/reports/semgrep-before.json`
- le rapport texte: `tache3/reports/semgrep-before.txt`
- Nombre d'erreurs: 0

Semprep a exécuté les 60 règles de sécurité de la version 1.184.0 du ruleset `p/java` sur 34 fichiers de `tika-serialization` et a trouvé 0 erreurs.

## Faire échouer le job de CI à l'encontre de nouvelles erreurs Semgrep

### Quel workflow a été modifié

Notre fork contient les workflow dùpstream de Tika, qui exécute le build Maven entier du projet, ainsi que le workflow que nous avons écrit pour la tâche 2, `tache2-tika-serialization.yml`,
qui build les dépendances de `tika-serialization` et exécute `ComponentNameResolverTest`.

Nous avons modifié `tache2-tika-serialization.yml`:

- Nous l'avons déjà utilisé pour le CI dans la tâche 2, donc il couvre un module sur lequel nous travaillons.
- Le run prneds environ 1 minute, plutôt que 5 minutes pour le build Maven entier.

### Changements

**Triggers.** Le workflow s'exécute à chaque push à la branche tache3 ou main, à chaque pull request, et manuellement.
**Nouveau job Semgrep.** Le job a été ajouté juste après le job `test`:

```yaml
semgrep:
    name: Semgrep (new findings only)
    runs-on: ubuntu-latest
    container: semgrep/semgrep:1.184.0
    permissions:
        contents: read
    steps:
        - uses: actions/checkout@v4
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
