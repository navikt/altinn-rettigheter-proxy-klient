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
AltinnrettigheterProxyKlientConfig config = 
    new AltinnrettigheterProxyKlientConfig(
        new ProxyConfig("sykefraværsstatistikk", altinnProxyUrl),
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

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* Lars Andreas Tveiten, lars.andreas.van.woensel.kooy.tveiten@nav.no
* Malaz Alkoj, malaz.alkoj@nav.no
* Thomas Dufourd, thomas.dufourd@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #arbeidsgiver-teamia.
