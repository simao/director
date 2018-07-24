CREATE TABLE `update_metadata`(
  `update_id` CHAR(36) NOT NULL,
  `metadata` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (update_id)
);

alter table `ecu_targets` ADD COLUMN `update_id` varchar(200) DEFAULT NULL
;
