import com.scrambler.detection.*;
import com.scrambler.config.CompanyDictionary;
import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.*;
import java.nio.file.Paths;
import java.util.regex.*;

public class SpotCheck {
  static FileInfo FI = new FileInfo(Paths.get("t"), "t", 1);
  public static void main(String[] a) {
    DetectionEngine e = new DetectionEngine();
    MaskingEngine m = new MaskingEngine();
    
    String[][] cases = {
      {"ICICI Bank", "team: ICICI Bank"},
      {"ICICI Lombard", "insurer: ICICI Lombard"},
      {"ICICI Prudential", "fund: ICICI Prudential"},
      {"bare domain", "host=icici.com"},
      {"bare www", "portal www.icicibank.com/login"},
      {"package id", "appId=com.csam.icici.bank.imobile"},
      {"ICICI-Bank full", "name=ICICI-Bank"},
      {"john@okicici", "upi=john@okicici"},
    };
    for (String[] c : cases) {
      var dr = e.detect(new DetectionContext(FI, c[1]));
      MappingRegistry reg = new MappingRegistry();
      String masked = m.mask(c[1], dr, reg);
      System.out.println("CASE: " + c[0]);
      System.out.println("  IN:  " + c[1]);
      System.out.println("  OUT: " + masked);
      dr.getEntities().forEach(en -> System.out.println("  ENT: " + en.getType() + " '" + en.getOriginalValue() + "'"));
      System.out.println();
    }
    Pattern p = CompanyDictionary.defaults().compilePattern();
    System.out.println("PATTERN: " + p.pattern());
  }
}
