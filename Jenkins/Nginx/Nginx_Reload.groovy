pipeline {
    agent any

    parameters {
        choice(
            name: 'NGINX_HOST',
            choices: ['both', 'web1', 'web2'],
            description: 'Select which Nginx server(s) to reload'
        )
    }

    environment {
        SSH_KEY = credentials('nginx-ssh-key')
    }

    stages {
        stage('Reload Nginx') {
            steps {
                script {
                    // ---------------------------
                    // Select target hosts
                    // ---------------------------
                    def targetHosts = []

                    if (params.NGINX_HOST == "both") {
 //                       targetHosts = ["172.31.42.57", "172.31.42.58"] // Replace with web1 & web2 IPs
                        targetHosts = ["172.31.42.57"]
                    } else if (params.NGINX_HOST == "web1") {
                        targetHosts = ["172.31.42.57"]
                    } else if (params.NGINX_HOST == "web2") {
                        targetHosts = ["172.31.42.58"]
                    }

                    // ---------------------------
                    // Reload Nginx on each host
                    // ---------------------------
                    targetHosts.each { host ->
                        echo "Reloading Nginx on server: ${host}"

                        sh """
                            ssh -o StrictHostKeyChecking=no -i ${SSH_KEY} ec2-user@${host} '
                                sudo nginx -t
                                sudo systemctl reload nginx
                                echo "Nginx reloaded successfully on ${host}"
                            '
                        """
                    }
                }
            }
        }
    }
}
