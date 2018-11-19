# What
[chromex-sample](https://github.com/binaryage/chromex-sample) but with Boot as
the build tool, plus a few other nicities.  Read that project's readme for a
description of the actual source code and architecture of the project.

# Why
Leiningen's purely declarative approach to project definitions and builds is not
well-suited for the complicated demands of Chrome extensions.

That project needs to duplicate various static resources like `manifest.json`
and `popup.html` between "dev" and "prod" setups to support the intricacies of
CLJS compilation and development tooling like figwheel, and also use bash
scripts to paper over the build outputs over when releasing.  Boot allows us to
easily dynamically generate those resources and do the equivalent papering-over
within the build.

Also, boot-cljs' `.cljs.edn` approach to producing CLJS modules is well-suited
to producing the different Chrome extension scripts in comparison to lein-cljs'.

# How
As the [chromex-sample](https://github.com/binaryage/chromex-sample) readme
describes, the dev environment for Chrome extensions is a bit janky.  Of the
three scripts/pages your extension can produce (background, popup, content),
figwheel is viable for the background and popup.  Chrome places much tighter
restrictions on the content script, so interactive dev tooling beyond a watch
build is not possible.

So, there are 2 relevant dev tasks: `dev-figwheel` and `dev-content-watch`.
`dev-figwheel` gives you a figwheel/REPL setup on the background and popup
scripts with `boot-cljs-repl` and `boot-figreload`, while `dev-content-watch`
gives you a simple watch/compile loop on the content script.

The `prod` task produces a build suitable for releasing the extension: advanced
optimizations, no extraneous files, etc.
