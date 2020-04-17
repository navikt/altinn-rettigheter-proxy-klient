Altinnrettigheter-proxy-klient
==============================

Bibliotek som tilbyr en Http klient til altinn-rettigheter-proxy.
Klienten vil forsøke å kontakte Altinn via proxyen, som tilbyr cacing på tvers av ulike tjenester i Nav. Klienten har en innebygd feilhåndtering, slik at dersom kall mot proxyen feiler vil det gjøres et nytt kall direkte mot Altinn sitt API.

# Komme i gang

Biblioteket er skrevet i Kotlin. Koden kompileres med maven og produserer en jar fil `altinn-rettigheter-proxy-klient-{version}.jar`

`mvn clean install`

# Bruk av AltinnrettigheterProxyKlient 

Biblioteket importeres i klientapplikasjon slik (eksempel med maven)
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

Listen av organisasjoner `AltinnReportee` der en bruker har spesifikk enkeltrettighet kan hentes på denne måten:
```java
List<AltinnReportee> organisasjoner =  
    klient.hentOrganisasjoner(
        tokenContext,
        new Subject(fnrInnloggetBruker),
        new ServiceCode(serviceCode),
        new ServiceEdition(serviceEdition)
    );
```

`tokenContext` hentes fra selvbetjeningstoken til innlogget bruker
```java
TokenContext  tokenContext = 
    contextHolder.getOIDCValidationContext().getToken(ISSUER_SELVBETJENING);
``` 

---
# Lage og publisere en ny release
## Forutsetning
Release tag skal være signert. Derfor må signering av commits være aktivert per default, med f.eks `git config commit.gpgsign true`

## Prosess
Vi bruker `mvn-release-plugin` for å lage en ny release. I den prosessen skal en ny tag genereres.
 Artifact publiseres fra tag-en med GitHub actions.

Start med å rydde opp etter forrige release om det trenges ved å kjøre `mvn release:clean`

Lag en ny release med `mvn release:prepare`

Kommandoen skal pushe en ny tag på GitHub. Da kan `Build and publish` action starte og release artifactene til Maven central.

## Publisere til Maven Central
Credentials som skal til for å kunne publisere til Maven Central provisjoneres av [publish-maven-central](https://github.com/navikt/publish-maven-central)

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* Lars Andreas Tveiten, lars.andreas.van.woensel.kooy.tveiten@nav.no
* Malaz Alkoj, malaz.alkoj@nav.no
* Thomas Dufourd, thomas.dufourd@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #arbeidsgiver-teamia.
