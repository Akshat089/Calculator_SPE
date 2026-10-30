# Jenkins setup notes

Step-by-step setup for running the `Jenkinsfile` in this repo. The pipeline uses `sh`
steps, so Jenkins must run on Linux — the easiest way on Windows/macOS is Jenkins in
Docker talking to the host's Docker daemon through the mounted socket.

## 1. Build a Jenkins image with Maven + Docker CLI + Compose

Create `jenkins.Dockerfile` somewhere outside the repo (it is not part of the app):

```dockerfile
FROM jenkins/jenkins:lts-jdk21
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven ca-certificates curl gnupg \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*
USER jenkins
```

```bash
docker build -t jenkins-docker -f jenkins.Dockerfile .
```

## 2. Run Jenkins with the Docker socket mounted

```bash
docker run -d --name jenkins --restart unless-stopped \
  -p 9090:8080 -p 50000:50000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /var/jenkins_home:/var/jenkins_home \
  --group-add 0 \
  jenkins-docker
```

Why each flag matters:

- **`-p 9090:8080`** — a natively installed Jenkins (or anything else) may already own
  host port 8080. Use `http://localhost:9090` for the UI, or stop the other Jenkins and
  use `-p 8080:8080`.
- **`-v /var/run/docker.sock:...`** — the pipeline's `docker` / `docker compose` commands
  run against the host daemon ("Docker outside of Docker"), so the deployed stack runs
  on the host next to Jenkins.
- **`-v /var/jenkins_home:/var/jenkins_home` (same path on both sides)** — required for
  the Deploy stage. `docker-compose.yml` bind-mounts `./nginx/nginx.conf`; Compose
  turns that into an absolute workspace path (`/var/jenkins_home/workspace/<job>/nginx/...`)
  which the *host* daemon resolves. With a named volume or a different host path, the
  file wouldn't exist on the host and nginx would fail to start. (On Docker Desktop the
  path lives inside the Docker VM, which is fine.)
- **`--group-add 0`** — lets the `jenkins` user use the socket. Check the socket's group
  with `docker run --rm -v /var/run/docker.sock:/s alpine stat -c %g /s` and use that
  GID if it isn't `0`.

Sanity check:

```bash
docker exec jenkins docker version
docker exec jenkins docker compose version
docker exec jenkins mvn -v
```

Unlock Jenkins with `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
and finish the setup wizard ("Install suggested plugins").

> **Native Windows Jenkins instead?** Replace every `sh '...'` in the `Jenkinsfile` with
> `bat '...'` (and adapt the shell loops in Deploy to PowerShell / batch), or make sure
> Git Bash's `sh.exe` is on the Jenkins service's PATH.

## 3. Plugins

*Manage Jenkins → Plugins → Available*, install (most come with "suggested plugins"):

- Git
- Pipeline
- Docker Pipeline
- JUnit
- Email Extension (`emailext`)
- GitHub (provides `githubPush()` and the `/github-webhook/` endpoint)

Restart Jenkins afterwards.

## 4. Docker Hub credentials (`dockerhub-creds`)

1. On Docker Hub: *Account Settings → Personal access tokens → Generate* (Read & Write).
2. In Jenkins: *Manage Jenkins → Credentials → System → Global credentials → Add Credentials*
   - Kind: **Username with password**
   - Username: your Docker Hub username
   - Password: the access token
   - ID: **`dockerhub-creds`** (must match the `Jenkinsfile`)
3. Edit the `environment` block at the top of the `Jenkinsfile`: set `DOCKERHUB_USER`
   and `NOTIFY_EMAIL`, commit and push.

## 5. Email (SMTP) setup

Example with Gmail (needs 2-step verification + an App Password):

1. Google Account → Security → App passwords → create one for "Jenkins".
2. *Manage Jenkins → Credentials*: add **Username with password**
   (username = Gmail address, password = app password), ID e.g. `smtp-creds`.
3. *Manage Jenkins → System*:
   - **Jenkins Location → System Admin e-mail address**: your Gmail address.
   - **Extended E-mail Notification**: SMTP server `smtp.gmail.com`, port `465`,
     *Advanced* → credentials `smtp-creds`, tick **Use SSL**.
     Default content type: `text/plain`.
   - (Optional) **E-mail Notification** section with the same values — use
     *Test configuration by sending test e-mail* to verify SMTP works.
4. Save.

## 6. Create the pipeline job

1. *New Item* → name `calculator-pipeline` → **Pipeline** → OK.
2. *Build Triggers*: tick **GitHub hook trigger for GITScm polling**
   (the `triggers { githubPush() }` block also registers this after the first run).
3. *Pipeline*: Definition **Pipeline script from SCM**
   - SCM: Git
   - Repository URL: `https://github.com/Akshat089/Calculator_SPE.git`
     (public repo needs no credentials)
   - Branch specifier: `*/main`
   - Script path: `Jenkinsfile`
4. Save → **Build Now** once manually (this also activates the trigger).

After a successful build: `http://localhost/api/v1/add?a=2&b=3` (served by nginx on the host).

## 7. GitHub webhook (local Jenkins via ngrok)

GitHub can't reach `localhost`, so expose Jenkins with ngrok:

```bash
ngrok config add-authtoken <your-ngrok-token>
ngrok http 9090          # the host port Jenkins is published on
```

Copy the `https://<random>.ngrok-free.app` URL, then on GitHub:

*Repo → Settings → Webhooks → Add webhook*

- Payload URL: `https://<random>.ngrok-free.app/github-webhook/` (trailing slash matters)
- Content type: `application/json`
- Events: **Just the push event**
- Active: ✓

GitHub sends a ping; it should show a green tick under *Recent Deliveries*.
The free ngrok URL changes on every restart — update the webhook when it does. If the
webhook can't be used, uncomment `pollSCM('H/5 * * * *')` in the `Jenkinsfile`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `permission denied ... docker.sock` | Wrong `--group-add` GID (see step 2). |
| `mvn: not found` | Jenkins image built without Maven (step 1). |
| nginx exits: `not a directory` / mount error | Jenkins home not mounted at the same host path (step 2). |
| `Bind for 0.0.0.0:80 failed` | Something else uses port 80: set `NGINX_PORT = '8081'` in the `Jenkinsfile`. |
| `denied: requested access to the resource is denied` on push | `DOCKERHUB_USER` doesn't match the account in `dockerhub-creds`, or token lacks write scope. |
| No email | Test SMTP from *Manage Jenkins → System*; check spam folder. |
