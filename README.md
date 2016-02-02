# yetibot-codeclimate

A Yetibot plugin to integrate with CodeClimate. **Requires Docker on the host.**

- [Official engines](https://docs.codeclimate.com/docs/list-of-engines)
- [Community engines](https://github.com/codeclimate-community)

If a CodeClimate Engine does not yet exist for your language, you can contribute
your own; [it's quite easy](http://blog.codeclimate.com/blog/2015/07/07/build-your-own-codeclimate-engine/).

![Yetibot CodeClimate](https://dl.dropboxusercontent.com/u/113427/yetibot-codeclimate-ruby.png)

## Roadmap

- [ ] Let Yetibot setup the webhook for you via a command

## Usage

Create a webhook in GitHub's repository settings to point to your Yetibot. The
path should be:

```
http://$YOUR_YETIBOT:3000/codeclimate/webhook
```

## Config

**workspace-path**: You can optionally set a workspace path. This is where
Yetibot will checkout repos to run CodeClimate analysis on. It's important to
note that if you're using Docker Machine, this location must be inside your
Users directory (on OSX). If not set, it defaults to "/tmp/cc", which works on
Linux where you don't need Docker Machine.

**results-path**: You can optionally set a path were linting results will be
stored.  The default is "./codeclimate/". Yetibot serves results from JSON files
stored in this directory.

```edn
;; merge this into config/config.edn in your yetibot installation
{:yetibot-codeclimate
  {:workspace-path "/Users/foo/tmp"
   :results-path "./codeclimate"}}
```

Make sure to set Yetibot's `:url` configuration. Yetibot CodeClimate uses this
to post the correct target_url on each commit it analyzes, which GitHub exposes
in their UI.

If you are using Docker Machine, you also must configure Yetibot's Docker
settings so CodeClimate uses the correct Docker Machine vars:

```edn
;; merge this into config/config.edn in your yetibot installation
:yetibot
{:url "http://YOUR_YETIBOT:3000"
 :docker
  {:machine {:DOCKER_TLS_VERIFY "1"
             :DOCKER_HOST "tcp://1.2.3.4:2376"
             :DOCKER_CERT_PATH "/Users/foo/.docker/machine/machines/default"
             :DOCKER_MACHINE_NAME "default"}}}
```

Find these settings using `docker-machine env default` where `default` is the
name of your Docker Machine (use `docker-machine ls` to list them if you don't
know the name).

## License

Copyright © 2016 Trevor C. Hartman

Distributed under the Eclipse Public License either version 1.0 or any later
version.
