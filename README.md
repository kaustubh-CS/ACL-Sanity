
## Prerequisites

- **Slack OAuth token** Save in Jenkins Global Credentials as `slack-bot-token` and invite the bot into the channel
- **Postman API key** Save in Jenkins Global Credentials as `postman_api_key`
- **Collection and Environment UID** 
  - From Postman UI, select the collection and run it, select the Run on CI/CD and configure
  - Select the  collection and the environment file, which has the scripts, the CLI command preview should show something like:
    ```bash
    sh 'postman collection run "339abc-gjhk-jhbv-defghd58d"-e "3390a-jbvbnm-bcdegfka-soi7449c626f"'
    ```

- Add that collection and environment UID into the Jenkinsfile
- Get the slack channel UID and hardcode it into Jenkinsfile

---
