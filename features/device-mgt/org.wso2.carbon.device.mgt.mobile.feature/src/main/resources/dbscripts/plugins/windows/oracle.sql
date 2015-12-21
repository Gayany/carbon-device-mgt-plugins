-- -----------------------------------------------------
-- Table `WIN_DEVICE`
-- -----------------------------------------------------
CREATE TABLE WIN_DEVICE (
  DEVICE_ID VARCHAR(45) NOT NULL,
  CHANNEL_URI VARCHAR(100) NULL DEFAULT NULL,
  DEVICE_INFO TEXT NULL DEFAULT NULL,
  IMEI VARCHAR(45) NULL DEFAULT NULL,
  IMSI VARCHAR(45) NULL DEFAULT NULL,
  OS_VERSION VARCHAR(45) NULL DEFAULT NULL,
  DEVICE_MODEL VARCHAR(45) NULL DEFAULT NULL,
  VENDOR VARCHAR(45) NULL DEFAULT NULL,
  LATITUDE VARCHAR(45) NULL DEFAULT NULL,
  LONGITUDE VARCHAR(45) NULL DEFAULT NULL,
  SERIAL VARCHAR(45) NULL DEFAULT NULL,
  MAC_ADDRESS VARCHAR(45) NULL DEFAULT NULL,
  DEVICE_NAME VARCHAR(100) NULL DEFAULT NULL,
  PRIMARY KEY (DEVICE_ID)
)
/

-- -----------------------------------------------------
-- Table `WIN_FEATURE`
-- -----------------------------------------------------
CREATE TABLE WIN_FEATURE (
  ID INT NOT NULL,
  CODE VARCHAR(45) NOT NULL,
  NAME VARCHAR(100) NOT NULL,
  DESCRIPTION VARCHAR(200) NULL,
  PRIMARY KEY (ID)
)
/

-- -----------------------------------------------------
-- Sequence `WIN_FEATURE_ID_INC_SEQ`
-- -----------------------------------------------------
CREATE SEQUENCE WIN_FEATURE_ID_INC_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/

-- -----------------------------------------------------
-- Trigger `WIN_FEATURE_ID_INC_TRIG`
-- -----------------------------------------------------
CREATE OR REPLACE TRIGGER WIN_FEATURE_ID_INC_TRIG
BEFORE INSERT
ON WIN_FEATURE
REFERENCING NEW AS NEW
FOR EACH ROW
  BEGIN
    SELECT WIN_FEATURE_ID_INC_SEQ.NEXTVAL INTO :NEW.ID FROM DUAL;
  END;
/