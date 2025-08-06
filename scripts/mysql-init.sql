-- Create databases
CREATE DATABASE IF NOT EXISTS docdemo;
CREATE DATABASE IF NOT EXISTS temporal;
CREATE DATABASE IF NOT EXISTS temporal_visibility;

-- Create users and grant permissions
CREATE USER IF NOT EXISTS 'doc'@'%' IDENTIFIED BY 'docpass';
CREATE USER IF NOT EXISTS 'temporal'@'%' IDENTIFIED BY 'temporal';

-- Grant permissions for application database
GRANT ALL PRIVILEGES ON docdemo.* TO 'doc'@'%';

-- Grant permissions for Temporal databases
GRANT ALL PRIVILEGES ON temporal.* TO 'temporal'@'%';
GRANT ALL PRIVILEGES ON temporal_visibility.* TO 'temporal'@'%';

-- Grant permissions to root as well (for temporal auto-setup)
GRANT ALL PRIVILEGES ON temporal.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON temporal_visibility.* TO 'root'@'%';

FLUSH PRIVILEGES;