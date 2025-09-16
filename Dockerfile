FROM openjdk:17

WORKDIR /usrapp/bin

ENV PORT 35000

COPY /target/classes /usrapp/bin/classes
COPY /target/dependency /usrapp/bin/dependency
COPY public /usrapp/bin/public

CMD ["java","-cp","./classes:./dependency/*","http.SimpleHttpServer"]
