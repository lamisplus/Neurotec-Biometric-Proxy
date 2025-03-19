# Neurotec-Biometric-Proxy
## System Requirements
### Prerequisites to Install
- IDE of choice (IntelliJ, Eclipse, etc.)
- Java 8+
- PostgreSQL 14+

## Run in Development Environment

### How to Install Dependencies
1. Install Java 8+
2. Install PostgreSQL 14+
3. Download and replace the system `.m2` folder.
4. Clone the git repository:
    ```bash
    git clone https://github.com/lamisplus/Core.git
    ```
5. Open the project in your IDE of choice.

### Update Configuration File
1. Update database access details in `db-config.yml` file.
2. Update other Maven application properties as required.

### Run Build and Install Commands
1. Change the directory to `src-module`:
    ```bash
    cd src-module
    ```
2. Run Frontend Build Command:
    ```bash
    npm run build
    ```
3. Run Maven clean install:
    ```bash
    mvn clean install
    ```

## How to Package for Production Environment
1. Run Maven package command:
    ```bash
    mvn clean package
    ```

## Launch Packaged JAR File
1. Launch the JAR file:
    ```bash
    java -jar <path-to-jar-file>
    ```
2. Optionally, run with memory allocation:
    ```bash
    java -jar -Xms4096M -Xmx6144M <path-to-jar-file>
    ```

## Visit the Application
- Visit the application on a browser at the configured port:
    ```
    http://localhost:8080
    ```

## Access Swagger Documentation
- Visit the application at:
    ```
    http://localhost:8080/swagger-ui.html#/
    ```

## Access Application Logs
- Application logs can be accessed in the `application-debug` folder.

## Authors & Acknowledgments
### Main contributors
- Emeka https://github.com/drjavanew
- Mathew Adegbite https://github.com/mathewade 
- John gichangi https://github.com/gichangi
- Victor Ajor   https://github.com/AJ-DataFI
