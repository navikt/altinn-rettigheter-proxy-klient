Altinnrettigheter-proxy-klient
==============================

Bibliotek som tilbyr en Http klient til altinn-rettigheter-proxy.

# Komme i gang

Biblioteket er skrevet i Kotlin. Koden kompileres med maven og produserer en jar fil `altinn-rettigheter-proxy-klient-{version}.jar`

`mvn clean install`

# Bruk av AltinnrettigheterProxyKlient 

Biblioteket importeres i klient applikasjon som følgende (eksempel med maven)
```xml
<dependency>
  <groupId>no.nav.arbeidsgiver</groupId>
  <artifactId>altinn-rettigheter-proxy-klient</artifactId>
  <version>${altinn-rettigheter-proxy-klient.version}</version>
</dependency>
```

Klienten instansieres slik: 
```java
String consumerId = "navn-til-klient-applikasjon";

AltinnrettigheterProxyKlientConfig config = 
    new AltinnrettigheterProxyKlientConfig(
        new ProxyConfig(consumerId, altinnProxyUrl),
        new AltinnConfig(altinnUrl, altinnApikey, altinnAPIGWApikey)
    );

AltinnrettigheterProxyKlient klient = new AltinnrettigheterProxyKlient(config, restTemplateBuilder);
```

Listen av organisasjoner `AltinnReportee` en bruker har enkel rettighet i kan hentes på denne måten:
```java
List<AltinnReportee> organisasjoner =  
    klient.hentOrganisasjoner(
        tokenContext,
        new Subject(fnrInnloggetBruker),
        new ServiceCode(serviceCode),
        new ServiceEdition(serviceEdition)
    );
```

`tokenContext` hentes fra selvbetjening token til innlogget bruker
```java
TokenContext  tokenContext = 
    contextHolder.getOIDCValidationContext().getToken(ISSUER_SELVBETJENING);
``` 

---
# Lage og publisere en ny release
## Forutsetning
Release tag skal være signert. Derfor signering av commits må være aktivert per default, med f.eks `git config commit.gpgsign true`

## Prosess
Vi bruker `mvn-release-plugin` for å lage en ny release. I den prosessen skal en ny tag genereres.
 Artifact publiseres fra tag-en med GitHub actions.

Start med å rydde opp etter forrige release om det trenges ved å kjøre `mvn release:clean`

Lag en ny release med `mvn release:prepare`

Kommandoen skal pushe en ny tag på GitHub. Da kan `Build and publish` action starte og release artifactene til Maven central.

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* Lars Andreas Tveiten, lars.andreas.van.woensel.kooy.tveiten@nav.no
* Malaz Alkoj, malaz.alkoj@nav.no
* Thomas Dufourd, thomas.dufourd@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #arbeidsgiver-teamia.
