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

CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(50) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING_ACTIVATION', -- PENDING_ACTIVATION, ACTIVE, LOCKED, DISABLED
    must_change_password BOOLEAN DEFAULT TRUE,
    failed_attempts INTEGER DEFAULT 0,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES accounts(id),
    token TEXT UNIQUE NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES accounts(id),
    action VARCHAR(50) NOT NULL, -- LOGIN, LOGOUT, CHANGE_PASSWORD, ACTIVATE
    ip_address VARCHAR(45),
    device_info TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed accounts from employees
-- Default password: password123 (BCrypt hash)
INSERT INTO accounts (employee_id, username, password_hash)
SELECT employee_id, username, '$2a$10$8.UnVuG9HHgffUDAlk8q6uyzREXBJHoS88LYUB7zhn9S73Cz.9Kdy'
FROM employees
ON CONFLICT (employee_id) DO NOTHING;
