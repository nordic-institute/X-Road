@Initialization
Feature: 0200 - SS: Message Log

  Background:
    Given Environment is initialized

  Scenario: SS0 Messagelogs are successfully archived and removed from database
    And message log "archive DEV" is triggered on "ss0"
    And Global configuration is fetched from "ss0"'s "proxy" for messagelog verification
    And messsagelog archives are downloaded from "ss0" "message-log-cli"
    Then "ss0" has 20 messagelogs present in the archives and all are cryptographically valid
    When message log "cleanup" is triggered on "ss0"
    Then "ss0" contains 0 messagelog entries

  Scenario: SS1 messagelog is successfully archived and removed from database
    When message log "archive DEV" is triggered on "ss1"
    And message log "cleanup" is triggered on "ss1"
    And messsagelog archives are downloaded from "ss1" "message-log-cli"
    Then "ss1" contains 0 messagelog entries

  Scenario: DEV/COM/4321 messagelogs can be decrypted with key 8A4BB80EEE081BDE
    Then "ss1" messsagelog archives "mlog-DEV_COM_4321" can be decrypted using key "8A4BB80EEE081BDE"
    And "ss1/8A4BB80EEE081BDE" has 10 messagelogs present in the archives and all are cryptographically valid

  Scenario: DEV/COM/4321 messagelogs can be decrypted with key E93952B01C2D2EA5
    Then "ss1" messsagelog archives "mlog-DEV_COM_4321" can be decrypted using key "E93952B01C2D2EA5"
    And "ss1/E93952B01C2D2EA5" has 10 messagelogs present in the archives and all are cryptographically valid

  Scenario: DEV/COM/1234 messagelogs can be decrypted with key 3BD9C292C63580F8
    When "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can be decrypted using key "3BD9C292C63580F8"
    And "ss1/3BD9C292C63580F8" has 2 messagelogs present in the archives and all are cryptographically valid

  Scenario: messagelogs decryption with other keys fails
    Given "ss1" messsagelog archives "mlog-DEV_COM_4321" can not be decrypted using key "3BD9C292C63580F8"
    And "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can not be decrypted using key "E93952B01C2D2EA5"
    And "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can not be decrypted using key "8A4BB80EEE081BDE"
