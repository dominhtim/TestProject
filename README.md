# Spring Boot 4.0 CRUD Application with JDK 21

This is a simple **To-Do Task CRUD (Create, Read, Update, Delete)**
application built with **Spring Boot 4.0**, **Java 21**, **Spring Data
JPA**, and the **H2 in-memory database**.

------------------------------------------------------------------------

## Prerequisites

-   **JDK 21** (Set in `pom.xml`)
-   **Maven 3.8+**

------------------------------------------------------------------------

## How to Run

### Clone the Repository

### Run the Application (using Maven)

    mvn spring-boot:run

The application will start on:\
**http://localhost:8080**

------------------------------------------------------------------------

## API Endpoints (CRUD)

All endpoints use the base URL:\
**http://localhost:8080/api/v1/tasks**

  ----------------------------------------------------------------------------------------------------------
  Method   Path      Description      Request Body (JSON)                                 Response Status
  -------- --------- ---------------- --------------------------------------------------- ------------------
  POST     `/`       Create a new     `{ "title": "New Task Title" }`                     200 OK
                     task.                                                                

  GET      `/`       Retrieve all     \-                                                  200 OK
                     tasks.                                                               

  GET      `/{id}`   Retrieve a       \-                                                  200 OK or 404 Not
                     single task by                                                       Found
                     ID.                                                                  

  PUT      `/{id}`   Update an        `{ "title": "Updated Title", "completed": true }`   200 OK or 404 Not
                     existing task.                                                       Found

  DELETE   `/{id}`   Delete a task by \-                                                  204 No Content or
                     ID.                                                                  404 Not Found
  ----------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## Database Console

The H2 in-memory database console is available for easy viewing at:\
**http://localhost:8080/h2-console**

**JDBC URL:** `jdbc:h2:mem:taskdb`\
**Username:** `sa`\
**Password:** `password`

------------------------------------------------------------------------

## Testing

To run the unit and integration tests:

    mvn test

------------------------------------------------------------------------

## Continuous Integration

The `.github/workflows/ci.yml` file sets up a GitHub Actions workflow
that automatically builds and tests the application using **JDK 21**
every time code is pushed or a pull request is created.
