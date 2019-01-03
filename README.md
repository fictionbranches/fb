# Fiction Branches

JAX-RS implementation of a collaborative interactive story. This is intended as a replacement to the current
engine at https://fictionbranches.net/

Fiction Branches is an online software engine which allows the production of multi-plotted stories. Each story
is divided into episodes comprising a single page of text. Each page will have a title, a link back to the
parent episode and the story text for that episode. At the end of each episode there can be one or more links
to subsequent episodes, plus the option to add another branch to the story at that point.

### To run the site, you must complete the database setup and either the Eclipe or command-line setups

## Database setup:

1. Install PostgreSQL 9.5 (or any recent version)
    - Linux/BSD users should install postgres from their package manager and use the included `psql` command line utility
        - On Ubuntu, `sudo -u postgres psql` will work. Other distributions may vary.
    - MacOS users are encouraged to use [Postgres.app](https://postgresapp.com/). Create and initialize a 9.5 database, enter the default database (will be named the same as your username), and continue to step 2.
    - Windows users are encouraged to use the graphical installer from [BigSQL](https://www.openscg.com/bigsql/postgresql/installers.jsp/). 
        - Use the default options when installing.
        - Open Command prompt.
        - Enter `psql postgres postgres`
        - Proceed to step 2. 

2. In `psql` or a SQL input, enter `create role fictionbranches login; create database fictionbranches owner fictionbranches;`
    - psql should respond with `CREATE ROLE` and `CREATE DATABASE`
    - exit psql using command `\q`

## Eclipse setup:

1. Install Java 8 (1.8) JDK

2. Install Eclipse

3. Open Eclipse > Workbench

4. File > Import > Git > Projects from Git > Next

5. Clone URI > Next

6. Enter https://github.com/fictionbranches/fb as the URI, it should auto fill the other fields

7. Next > Next

8. Choose a directory to clone the repo to > Next > Next > Finish

9. After project imports and syncs (bottom right), on the left side of the scree open fb >  src/main/java > fb.db > Right click InitDB > Run as > Java Application

10. Answer the prompts in the Console at the bottom

11. On the left side > fb > src/main/java > fb > Right click InitSite > Run as > Java Application

12. Once you see `Server started`, the site is accessible at [https://localhost:8080](https://localhost:8080). You will have to accept the invalid certificate. 

## Command line Setup:

1. Install Java 8 (1.8) JDK

2. Clone repository (`git clone https://github.com/fictionbranches/fb`)

3. Enter project directory (`cd ./fb/fb`)

4. `gradlew run -PrunArgs="firstrun"` to initialize the database. It will prompt you several times for information.

5. `gradlew run` will run the backend, by default in dev mode.
    - Site is now accessible at [https://localhost:8080](https://localhost:8080). You will have to accept the invalid certificate.

6. `./gradlew jar` to build a runnable jar, including all dependencies
    - jar will be written to build/libs/fb.jar
    - run with `java -Xmx2048m -jar build/libs/fb.jar`

Note that when the server starts, it will look for a directory in the current working directory called "fbstuff", and will attempt to create it if it doesn't exist. This is where the backend stores the log file, search indexes, and temporary session data between restarts. 

When you cleanly (using the Eclipse stop button, ctrl+c, SIGINT, etc (or whatever the Windows equivalents of those are)) shut down the server, it will write active login sessions to json files in the current working directory/fbstuff/fbtemp. When the server starts, it will look for this directory and, if it finds it, read in the json files and delete the directory. 

