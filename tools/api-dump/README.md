# API dump (for development)

Minecraft 26.3 renamed much of its client API, and the dev sandbox can't download Minecraft, so exact
class signatures are read from CI instead:

1. Create a branch `ci/api-dump` from `main`.
2. Copy `api-dump.yml` to `.github/workflows/`, `emit.py` to the repo root, and add this task to `build.gradle`:

   ```groovy
   tasks.register('printClasspath') {
       def cp = sourceSets.client.compileClasspath
       def out = layout.buildDirectory.file('cp.txt')
       doLast { def f = out.get().asFile; f.parentFile.mkdirs(); f.text = cp.asPath }
   }
   ```
3. Edit the `dump` lines in the workflow to list the classes you need, push the branch.
4. The `javap` output is attached to the run as annotations (`dump-000`, `dump-001`, ...), readable through
   the GitHub checks API. Delete the branch afterwards.
