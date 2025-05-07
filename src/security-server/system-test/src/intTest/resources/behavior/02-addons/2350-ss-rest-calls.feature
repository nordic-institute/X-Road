@SecurityServer
@Addon
Feature: 2350 - SS: Call REST and OPENAPI3 methods

  Scenario: Call REST and OPENAPI3 methods
    Given Security Server saved endpoint REST method was sent for client "DEV/COM/1234"
    Given Security Server saved endpoint OPENAPI3 method was sent for client "DEV/COM/1234"
    Given Security Server not saved endpoint OPENAPI3 method was sent for client "DEV/COM/1234"
