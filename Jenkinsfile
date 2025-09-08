pipeline {
  agent any

  environment {
    SLACK_CRED    = 'slack-bot-token'   // Jenkins Secret Text credential ID for Slack bot
    SLACK_CHANNEL = 'C09DRNQSBNJ'       // Slack channel ID (hardcoded)
    REPORT_REPO   = 'https://github.com/kaustubh-CS/report-generator.git'
  }

  stages {
    stage('Checkout Report Generator') {
      steps {
        sh '''
          echo "Cloning report-generator repo..."
          rm -rf report-generator
          git clone $REPORT_REPO
        '''
      }
    }
    
    stage('Install Python (once per agent)') {
      steps {
        sh '''
          echo "Installing Python & pip..."
          apt-get update -y
          apt-get install -y python3 python3-pip python3.11-venv
        '''
      }
    }

    stage('Run Collection with Newman') {
      steps {
        withCredentials([string(credentialsId: 'postman_api_key', variable: 'POSTMAN_API_KEY')]) {
          sh '''
            echo "Running Newman collection..."
            docker run --rm \
              --volumes-from jenkins \
              -w "$WORKSPACE" \
              -e POSTMAN_API_KEY="$POSTMAN_API_KEY" \
              postman/newman:5-alpine \
              run "https://api.getpostman.com/collections/33905369-5918dd27-0dfc-4f60-b62a-f6580aba239e?apikey=$POSTMAN_API_KEY" \
              -e "https://api.getpostman.com/environments/33905369-697e48db-8afb-41c3-9739-4547449c626f?apikey=$POSTMAN_API_KEY" \
              --reporters cli,json \
              --reporter-json-export results.json

            echo "Listing workspace after Newman run:"
            ls -lah
          '''
        }
      }
    }

    stage('Generate Styled HTML Report') {
      steps {
        sh '''
          echo "Generating HTML report from results.json..."
          cd report-generator
          python3 -m venv venv
          . venv/bin/activate
          pip install -r requirements.txt --quiet
          python generate_report.py ../results.json ../results.html
          deactivate
          cd ..
          ls -lh results.html
        '''
      }
    }

    stage('Upload Report to Slack') {
      steps {
        script {
          def reportUrl = "${env.BUILD_URL}Newman_20Report/results.html"

          // Post a message with link to Jenkins-hosted HTML
          slackSend (
            channel: "${SLACK_CHANNEL}",
            message: "*${env.JOB_NAME}* #${env.BUILD_NUMBER} completed. View report: ${reportUrl}",
            tokenCredentialId: "${SLACK_CRED}"
          )

          // Upload the HTML file directly into Slack
          slackUploadFile (
            filePath: 'results.html',
            channel: "${SLACK_CHANNEL}",
            initialComment: "Full HTML Report for *${env.JOB_NAME}* #${env.BUILD_NUMBER}",
            credentialId: "${SLACK_CRED}"
          )
        }
      }
    }
  }

  post {
    always {
      publishHTML(target: [
        reportName: 'Newman Report',
        reportDir: '.',
        reportFiles: 'results.html',
        keepAll: true,
        allowMissing: false,
        alwaysLinkToLastBuild: true
      ])
      archiveArtifacts artifacts: 'results.json, results.html', fingerprint: true
    }
  }
}