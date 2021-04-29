FROM openjdk:8-jre

#################################################################
# Install subjack
RUN apt update
RUN apt install git -y
RUN wget https://golang.org/dl/go1.15.2.linux-amd64.tar.gz
RUN tar -C /usr/local -xzf go1.15.2.linux-amd64.tar.gz
RUN export PATH=$PATH:/usr/local/go/bin
RUN /usr/local/go/bin/go get github.com/certmichelin/subjack
RUN rm -rf /subjack

#Install subfinder
RUN wget https://github.com/projectdiscovery/subfinder/releases/download/v2.4.7/subfinder_2.4.7_linux_amd64.tar.gz
RUN tar xvfz subfinder_2.4.7_linux_amd64.tar.gz
RUN chmod +x ./subfinder
RUN mv ./subfinder /usr/bin

#Create config directory and config file
RUN mkdir /root/.config/
RUN mkdir /root/.config/subfinder/
RUN touch /root/.config/subfinder/config.yaml

WORKDIR /
#################################################################

ARG JAR_FILE=target/app.jar
COPY ${JAR_FILE} app.jar
CMD ["java", "-Dlogging.config=/conf/log4j2.xml","-jar","/app.jar"]
