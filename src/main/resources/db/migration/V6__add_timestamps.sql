ALTER TABLE `current_images`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `device_current_target`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `device_update_targets`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `ecu_targets`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `ecus`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `file_cache`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `file_cache_requests`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `repo_names`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;

ALTER TABLE `root_files`
ADD `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
;
