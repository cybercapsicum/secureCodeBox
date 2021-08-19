package io.securecodebox.persistence.service;

import io.securecodebox.models.V1ScanSpec;
import io.securecodebox.persistence.config.PersistenceProviderConfig;
import io.securecodebox.persistence.defectdojo.models.ScanFile;
import io.securecodebox.persistence.models.Scan;
import io.securecodebox.persistence.service.scanresult.ScanResultService;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScanServiceTest {
  @Mock
  S3Service s3Service;
  @Mock
  PersistenceProviderConfig ppConfig;
  @Mock
  Scan scan;

  public static String readResourceAsString(String resourceName) throws IOException {
    ClassLoader cl = ScanServiceTest.class.getClassLoader();
    File resourceFile = new File(cl.getResource(resourceName).getFile());
    return Files.readString(resourceFile.toPath());
  }

  /***
   * When a unknown scanner is encountered the findings json has to be converted into defectdojo compatible json and
   * the correct file ending must be used so DefectDojo can choose the right parser (usually xml or json)
   * @throws IOException
   * @throws InterruptedException
   * @throws JSONException
   */
  @Test
  public void correctlyParsesGenericResults() throws IOException, InterruptedException, JSONException {
    // read data
    String expectedDdFindingsString = readResourceAsString("kubehunter-dd-findings.json");
    String givenScbFindingsString = readResourceAsString("kubehunter-scb-findings.json");

    // check if null or empty
    assertNotEquals(expectedDdFindingsString, "");
    assertNotEquals(givenScbFindingsString, "");
    String findingsUrl = "https://foo.com/findings.json";
    V1ScanSpec scanSpec = new V1ScanSpec();
    scanSpec.setScanType("some-unknown-scanner-type");

    // mock methods
    when(scan.getSpec()).thenReturn(scanSpec);
    when(ppConfig.getFindingDownloadUrl()).thenReturn(findingsUrl);
    when(ppConfig.getDefectDojoTimezoneId()).thenReturn(ZoneId.of("+0"));
    when(s3Service.downloadFile(findingsUrl)).thenReturn(givenScbFindingsString);

    // test for correctness
    var result = ScanResultService.build(scan, s3Service).getScanResult(ppConfig);
    // check that the produced and expected JSON are the same. Non-strict array ordering and not extensible.
    JSONAssert.assertEquals(expectedDdFindingsString, result.getContent(), JSONCompareMode.NON_EXTENSIBLE);
    // file name must have the right ending
    assertTrue(result.getName().endsWith(".json"));
  }

  /***
   * When a known scanner like nikto is encountered the raw result must be passed and the correct file ending must
   * be used so DefectDojo can choose the right parser (usually xml or json)
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void correctlyReturnsScannerSpecificResults() throws IOException, InterruptedException {
    var rawNiktoScanString = readResourceAsString("nikto-raw-result.json");
    String rawResultDownloadUrl = "https://foo.com/nikto-raw-results.json";
    V1ScanSpec scanSpec = new V1ScanSpec();
    scanSpec.setScanType("nikto");
    when(ppConfig.getRawResultDownloadUrl()).thenReturn(rawResultDownloadUrl);
    when(scan.getSpec()).thenReturn(scanSpec);
    when(s3Service.downloadFile(rawResultDownloadUrl)).thenReturn(rawNiktoScanString);
    ScanFile result = ScanResultService.build(scan, s3Service).getScanResult(ppConfig);
    assertEquals(rawNiktoScanString, result.getContent());
    assertTrue(result.getName().endsWith(".json"));
  }
}
