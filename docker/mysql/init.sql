-- MySQL initialization script for Quarkus User Management API
-- This script ensures the database is properly configured for UTF8MB4

-- Set default character set and collation
ALTER DATABASE userapi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant necessary privileges to the user
GRANT ALL PRIVILEGES ON userapi.* TO 'userapi'@'%';
FLUSH PRIVILEGES;

-- Show database configuration for verification
SELECT 
    SCHEMA_NAME,
    DEFAULT_CHARACTER_SET_NAME,
    DEFAULT_COLLATION_NAME
FROM INFORMATION_SCHEMA.SCHEMATA 
WHERE SCHEMA_NAME = 'userapi';