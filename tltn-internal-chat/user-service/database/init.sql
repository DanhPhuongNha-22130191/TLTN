CREATE TABLE IF NOT EXISTS employees (
    employee_id VARCHAR(50) PRIMARY KEY,
    full_name TEXT,
    username TEXT,
    email TEXT,
    phone_number VARCHAR(20),
    department TEXT,
    team TEXT,
    position TEXT,
    level TEXT,
    manager TEXT,
    location TEXT,
    country TEXT,
    timezone TEXT,
    employee_type TEXT,
    status TEXT,
    start_date DATE,
    probation_end_date DATE,
    skills TEXT,
    projects TEXT,
    github TEXT,
    slack TEXT,
    remaining_leave_days INTEGER
);

-- Copy data from CSV
-- The CSV file will be mounted to /tmp/data.csv in the container
COPY employees (
    employee_id, full_name, username, email, phone_number, 
    department, team, position, level, manager, 
    location, country, timezone, employee_type, status, 
    start_date, probation_end_date, skills, projects, github, 
    slack, remaining_leave_days
)
FROM '/tmp/data.csv'
DELIMITER ','
CSV HEADER
ENCODING 'UTF8';
