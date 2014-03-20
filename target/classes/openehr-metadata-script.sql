delimiter $$

CREATE TABLE `openehr_form_metadata` (
  `metadata_id` int(11) NOT NULL AUTO_INCREMENT,
  `form_id` int(11) DEFAULT NULL,
  `concept_id` int(11) DEFAULT NULL,
  `widget_type` varchar(255) DEFAULT NULL,
  `label` varchar(255) DEFAULT NULL,
  `path` varchar(1024) DEFAULT NULL,
  `uuid` char(38) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  `default_value` varchar(255) DEFAULT NULL,
  `in_repeat` int(11) DEFAULT NULL,
  PRIMARY KEY (`metadata_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32455 DEFAULT CHARSET=utf8$$

INSERT INTO `openmrs`.`concept_class`
(`concept_class_id`,
`name`,
`description`,
`creator`,
`date_created`,
`uuid`)
VALUES
(
17,
'OpenEHR',
'Automatically generated concept from openEHR Template',
1,
'2013-04-23 17:15:01',
'8d492ff0-c2cc-11de-8d13-0010c6dffd0z'
);





