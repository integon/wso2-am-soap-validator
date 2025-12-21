# SOAP Validation Mediator for WSO2 API Manager 4.6.0

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## Overview

**SOAP Validation Mediator** is a WSO2 API Manager 4.6.0 mediator that validates SOAP 1.1 and 1.2 messages against a given WSDL and its dependencies.  

This mediator ensures that incoming or outgoing SOAP requests and responses conform to the WSDL-defined structure, including:

- Correct SOAP version (1.1 or 1.2)
- SOAP body element validation
- Validation against complex WSDL/XSD dependencies
- Proper SOAP fault generation when validation fails

It is designed to integrate seamlessly with WSO2 API Manager and can be used in both inbound and outbound flows.

## Features

- **Full SOAP 1.1 and 1.2 support** – detects SOAP version automatically.
- **Schema validation** – validates SOAP messages against WSDL/XSD schemas.
- **SOAP fault handling** – generates proper SOAP 1.1/1.2 faults on validation errors.
- **Support for complex WSDLs** – handles WSDLs with imports and ZIP archives.
- **Caching** – schemas and service metadata are cached for performance.

## Installation

1. Build the mediator JAR using Maven:

    ```bash
    mvn clean package
    ```

2. Copy the JAR to your WSO2 API Manager `<APIM_HOME>/repository/components/lib/` directory.

3. (Re)Start the API Manager.

## Configuration

To use the mediator in the WSO2 API Manager, create a .j2 file with the following (minimum) content:
```xml
<class name="ch.integon.wso2.am.mediator.wsdl.SOAPValidationMediator"/>
```
Then [create a policy](https://apim.docs.wso2.com/en/latest/manage-apis/design/api-policies/create-policy/) (operational- or api-level) for SOAP api's. The mediator can handle `Request`, `Response` and `Fault` application flows


## Usage

Once the mediator is in place, it will:

- Validate inbound SOAP requests before they reach your API backend.
- Validate outbound SOAP responses before they are sent to clients.
- Validate fault SOAP responses before they are sent to clients.
- Automatically generate SOAP faults if the payload is invalid.

Validation errors are logged in the WSO2 server logs for debugging purposes.

## Running Tests

The repository includes a Makefile to simplify testing in a local Docker setup.

```bash
make build   # Builds the JAR and places it in a folder to be mounted later
make up      # Starts the WSO2 API Manager and a SOAP mock backend
make setup   # Installs all the test APIs
make down    # Stops and removes the Docker containers
```
All tests are deployed in a [SoapUI Project](https://www.soapui.org/tools/soapui/) here: `tests/resources/apis/SOAPValidator-soapui-project.xml`

## License

This project is licensed under the [Apache License 2.0](LICENSE).

