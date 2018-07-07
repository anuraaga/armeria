# Armeria DocService client

A webapp that shows the Armeria Docs page.

## Developing

To develop, start the dev server using 

```bash
$ yarn
$ yarn run start
```

This will usually not be useful since without a server running, the client does not have any spec it can render.
You can have server calls proxied to a running Armeria server by specifying the `PROXY_PORT` environment
variable, e.g.,

```bash
$ PROXY_PORT=51234 yarn run start
```

Replacing the port of a docs page in the running server with `3000` will use the dev server to render while
proxying all server calls to the actual Armeria server.

## Updating licenses

When changing a dependency (i.e., when the `yarn.lock` file changes), refresh license information by running

```bash
$ yarn licenses generate-disclaimer --prod > 3rd-party-licenses.txt
```
