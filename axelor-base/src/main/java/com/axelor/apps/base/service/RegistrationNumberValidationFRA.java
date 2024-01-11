package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.RegistrationNumberTemplate;
import com.axelor.common.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class RegistrationNumberValidationFRA implements RegistrationNumberValidation {
  public boolean computeRegistrationCodeValidity(String registrationCode) {
    int sum = 0;
    boolean isOddNumber = true;
    registrationCode = registrationCode.replace(" ", "");
    if (registrationCode.length() != 14) {
      return false;
    }
    int i = registrationCode.length() - 1;
    while (i > -1) {
      int number = Character.getNumericValue(registrationCode.charAt(i));
      if (number < 0) {
        i--;
        continue;
      }
      if (!isOddNumber) {
        number *= 2;
      }
      if (number < 10) {
        sum += number;
      } else {
        number -= 10;
        sum += number + 1;
      }
      i--;
      isOddNumber = !isOddNumber;
    }
    return sum % 10 == 0;
  }

  protected String getTaxKeyFromSIREN(String sirenStr) {
    int siren = Integer.parseInt(sirenStr);
    int taxKey = Math.floorMod(siren, 97);
    taxKey = Math.floorMod(12 + 3 * taxKey, 97);
    return String.format("%02d", taxKey);
  }

  protected String getTaxNbrFromRegistrationCode(Partner partner) {
    String taxNbr = "";

    if (partner.getMainAddress() != null
        && partner.getMainAddress().getAddressL7Country() != null) {
      String countryCode = partner.getMainAddress().getAddressL7Country().getAlpha2Code();
      String regCode = partner.getRegistrationCode();

      if (regCode != null) {
        regCode = regCode.replaceAll(" ", "");

        if (regCode.length() == 14) {
          String siren = regCode.substring(0, 9);
          String taxKey = getTaxKeyFromSIREN(siren);

          taxNbr = String.format("%s%s%s", countryCode, taxKey, siren);
        }
      }
    }

    return taxNbr;
  }

  protected String getNicFromRegistrationCode(Partner partner) {
    String regCode = partner.getRegistrationCode();
    String nic = "";

    if (partner.getMainAddress() != null && partner.getMainAddress().getAddressL7Country() != null) {
      Country businessCountry = partner.getMainAddress().getAddressL7Country();
      RegistrationNumberTemplate registrationNumberTemplate =
          businessCountry.getRegistrationNumberTemplate();
      if (registrationNumberTemplate.getUseNic() && regCode != null) {
        regCode = regCode.replaceAll(" ", "");

        if (regCode.length() == registrationNumberTemplate.getRequiredSize()) {
          nic =
              regCode.substring(
                  registrationNumberTemplate.getNicPos() - 1,
                  registrationNumberTemplate.getNicPos()
                      + registrationNumberTemplate.getNicLength()
                      - 1);
        }
      }
    }

    return nic;
  }

  public String getSirenFromRegistrationCode(Partner partner) {
    String regCode = partner.getRegistrationCode();
    String siren = "";

    if (partner.getMainAddress() != null && partner.getMainAddress().getAddressL7Country() != null) {
      Country businessCountry = partner.getMainAddress().getAddressL7Country();
      RegistrationNumberTemplate registrationNumberTemplate =
          businessCountry.getRegistrationNumberTemplate();
      if (registrationNumberTemplate.getUseSiren() && regCode != null) {
        regCode = regCode.replaceAll(" ", "");

        if (regCode.length() == registrationNumberTemplate.getRequiredSize()) {
          siren =
              regCode.substring(
                  registrationNumberTemplate.getSirenPos() - 1,
                  registrationNumberTemplate.getSirenPos()
                      + registrationNumberTemplate.getSirenLength()
                      - 1);
        }
      }
    }
    return siren;
  }

  @Override
  public Map<String, Map<String, Object>> getRegistrationCodeValidationAttrs(Partner partner) {
    Map<String, Map<String, Object>> attrsMap = new HashMap<>();

    boolean useNic = false;
    boolean useSiren = false;
    if (partner.getMainAddress() != null && partner.getMainAddress().getAddressL7Country() != null) {
      RegistrationNumberTemplate registrationNumberTemplate =
              partner.getMainAddress().getAddressL7Country().getRegistrationNumberTemplate();
      if (!registrationNumberTemplate.getUseNic()) {
        useNic = true;
      }
      if (!registrationNumberTemplate.getUseSiren()) {
        useSiren = true;
      }
    }

    if(!StringUtils.isBlank(partner.getRegistrationCode())) {
      String taxNbr = getTaxNbrFromRegistrationCode(partner);
      String nic = getNicFromRegistrationCode(partner);
      String siren = getSirenFromRegistrationCode(partner);

      attrsMap.put("taxNbr", Map.of("value", taxNbr));
      attrsMap.put("nic", Map.of("value", nic, "hidden", useNic));
      attrsMap.put("siren", Map.of("value", siren, "hidden", useSiren));
    }else{
      attrsMap.put("nic", Map.of("value", "", "hidden", useNic));
      attrsMap.put("siren", Map.of("value", "", "hidden", useSiren));
    }

    return attrsMap;
  }
}
