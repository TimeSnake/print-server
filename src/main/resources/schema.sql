CREATE SEQUENCE application_user_seq INCREMENT BY 50 START WITH 1;

CREATE SEQUENCE print_job_seq INCREMENT BY 50 START WITH 1;

CREATE SEQUENCE printer_seq INCREMENT BY 50 START WITH 1;

CREATE TABLE application_user
(
    id              BIGINT       NOT NULL,
    username        VARCHAR(255) NULL,
    name            VARCHAR(255) NULL,
    hashed_password VARCHAR(255) NULL,
    profile_picture BLOB         NULL,
    CONSTRAINT pk_application_user PRIMARY KEY (id)
);

CREATE TABLE print_job
(
    id             BIGINT       NOT NULL,
    cups_id        VARCHAR(255) NOT NULL,
    document_pages INT          NOT NULL,
    selected_pages INT          NOT NULL,
    printed_pages  INT          NOT NULL,
    costs          DOUBLE       NOT NULL,
    printer_id     BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    file_name      VARCHAR(255) NULL,
    timestamp      datetime     NOT NULL,
    CONSTRAINT pk_print_job PRIMARY KEY (id)
);

CREATE TABLE printer
(
    id              BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    priority        INT          NOT NULL,
    cups_name       VARCHAR(255) NOT NULL,
    price_one_sided DOUBLE       NOT NULL,
    price_two_sided DOUBLE       NOT NULL,
    CONSTRAINT pk_printer PRIMARY KEY (id)
);

CREATE TABLE user_roles
(
    user_id BIGINT       NOT NULL,
    roles   VARCHAR(255) NULL
);

ALTER TABLE printer
    ADD CONSTRAINT uc_printer_priority UNIQUE (priority);

ALTER TABLE print_job
    ADD CONSTRAINT FK_PRINT_JOB_ON_PRINTER FOREIGN KEY (printer_id) REFERENCES printer (id);

ALTER TABLE print_job
    ADD CONSTRAINT FK_PRINT_JOB_ON_USER FOREIGN KEY (user_id) REFERENCES application_user (id);

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_on_user FOREIGN KEY (user_id) REFERENCES application_user (id);