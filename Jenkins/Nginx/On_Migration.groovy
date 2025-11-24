pipeline {
    agent any

    parameters {
        choice(
            name: 'NGINX_HOST',
            choices: ['both', 'web1', 'web2'],
            description: 'Select which Nginx server(s) to modify'
        )

        choice(
            name: 'SERVER_TO_DISABLE',
            choices: [
                '172.20.4.102:9090',
                '172.20.5.28:9090',
                '172.20.4.181:9090'
            ],
            description: 'Select the upstream server to enable or disable'
        )

        choice(
            name: 'ACTION',
            choices: ['DISABLE', 'ENABLE'],
            description: 'Disable (comment) or enable (uncomment) server'
        )

        choice(
            name: 'RELOAD_NGINX',
            choices: ['YES', 'NO'],
            description: 'Do you want to reload Nginx at the end?'
        )
    }

    environment {
        SSH_KEY = credentials('nginx-ssh-key')
        NGINX_FILE = "/etc/nginx/sites-enabled/localhost"
    }

    stages {
        stage('Apply Nginx Configuration Changes') {
            steps {
                script {

                    // ---------------------------
                    // Select target hosts
                    // ---------------------------
                    def targetHosts = []
                    if (params.NGINX_HOST == "both") {
                        targetHosts = ["172.31.42.57"]
                    } else if (params.NGINX_HOST == "web1") {
                        targetHosts = ["172.31.42.57"]
                    } else if (params.NGINX_HOST == "web2") {
                        targetHosts = ["172.31.42.57"]
                    }

                    def server = params.SERVER_TO_DISABLE
                    def cmd = ""

                    // ---------------------------
                    // Generate sed commands
                    // ---------------------------
                    if (server == "172.20.4.102:9090" && params.ACTION == "DISABLE") {

                        cmd = """
                            sudo sed -i "s/^ *server 172.20.4.102:9090/# server 172.20.4.102:9090/" ${NGINX_FILE}
                            sudo sed -i "/pgRequestServer/,/}/ s/ backup//g" ${NGINX_FILE}
                        """

                    } else if (server == "172.20.4.102:9090" && params.ACTION == "ENABLE") {

                        cmd = """
                            sudo sed -i "s/^# server 172.20.4.102:9090/server 172.20.4.102:9090/" ${NGINX_FILE}
                            sudo sed -i "/pgRequestServer/,/}/ s/server 172.20.5.28:9090;/server 172.20.5.28:9090 backup;/" ${NGINX_FILE}
                            sudo sed -i "/pgRequestServer/,/}/ s/server 172.20.4.181:9090;/server 172.20.4.181:9090 backup;/" ${NGINX_FILE}
                        """

                    } else if (params.ACTION == "DISABLE") {

                        cmd = """
                            sudo sed -i "s/^ *server ${server}/# server ${server}/" ${NGINX_FILE}
                        """

                    } else if (params.ACTION == "ENABLE") {

                        cmd = """
                            sudo sed -i "s/^# server ${server}/server ${server}/" ${NGINX_FILE}
                        """
                    }

                    // ---------------------------
                    // Build remote command
                    // ---------------------------
                    def remoteCmd = """
                        ${cmd}
                        sudo nginx -t
                        echo "\\n======= Showing first 200 lines of localhost config ======="
                        sudo sed -n '1,200p' ${NGINX_FILE}
                    """

                    // Reload at the very end if selected
                    if (params.RELOAD_NGINX == "YES") {
                        remoteCmd += """
                            sudo systemctl reload nginx
                            echo "Nginx reloaded successfully!"
                        """
                    }

                    // ---------------------------
                    // Execute on selected hosts
                    // ---------------------------
                    targetHosts.each { host ->
                        echo "Applying changes on Nginx server: ${host}"

                        sh """
                            ssh -o StrictHostKeyChecking=no -i ${SSH_KEY} ec2-user@${host} '
                                ${remoteCmd}
                            '
                        """
                    }
                }
            }
        }
    }
}
