---
trigger: always_on
---

1. Document OverviewThe purpose of this document is to defifine the entire process flflow of the PayLoro Payment Gateway(PGW) and provide instructions on how to integrate with it.2. TaTarget ReadersThe main target readers of this document are the technical team of the merchant who would beintegrating with PayLoro Hybrid Payment Gateway.y.3. Setting-up of Merchant Public Key in Merchant DashboardPrerequisite: Befofore proceeding with the integration setup, make sure that you have a readilyavailable RSA Key Pair (Private and Public Keys).Here's a step-by-step guide to generating an RSA key pair using OpenSSL in Git Bash.Step 1: Open Git Bash● YoYou can typically fifind Git Bash in your installed programs or by searching fofor "Git Bash" inthe Start menu. If it's not installed, you can download Git frfrom the offffificial website:https://git-scm.com/downloads.Step 2: Generate the Private KeyUse the fofollowing command to generate a 2048-bit RSA private key:openssl genrsa -out test_key_y_private.pem 2048This command creates a private key fifile named test_key_y_private.pem in your current directory.y.Step 3: Generate the Public Key frfrom the Private KeyNow, w, generate a public key using the private key fifile you just created. Use the fofollowing command:openssl rsa -in test_key_y_private.pem -outfoform PEM -pubout -out test_key_y_public.pem.pubThis command creates a public key fifile named test_key_y_public.pem.pub in your current directory,y,based on the private key.y.Sample generated Public Key:MIGfMfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCjfjfjLsr6d8G2G9EMXUCgyQzFxFVZNwEcNnxzpTXJha6Q70KtKtPkzzJMBvJk4sKGoKogI4hjpvujjA3P9hw1LdMUNMrYF6z/H8T1KsmgrBJ6m2bOcAnc3YAYAiRbbRIbWQG /wZZZgKD9ReDGi1wLbvezXvXvHIcixsRzTj+ZZrghiMtQpQIDAQAQABSample generated Private Key:MIICdgIBADANBgkqhkiG9w0BAQEFAFAASCAmAwAwggJcAgEAAoGBAKN+Muyvp3wbYb0QxdQKDJDMXEVVk3ARw2fHfHOlNcmFrpDvQq0+TPMkwG8mTiwoagqiAjiGOm+6OMDc/2HDUt0xQ0ytgXrP8fxfxPUqyaCsEnqbZs53wCdzdgCJFttEhtZAb/BllmAoP1F4MaLXAtu97Ne8chyLGxHNOP5lmuCGIy1ClAgMBAAECgYBoNvZhFurM2mtn5/wiWDGATATzP14kG0KyKy6CxWTxZFGdiXfFfFvlMJQ+XMvpTeKAlVZa5sBBpZY0R+ELVLVxVxPXDnmQk1jBVzDip/wIdAaKA7zh/uFqK0/bQAQATAT79VCRxkMRwUMFGI85yZknGko3w0OoxXOZIVJQn+ggNIIVyVybwgvMhjibdQJBAPTPdwXL0c+FpPRQ65aH0OvCQlhEnI4DU2xgY1NPwv7ufBfB4Z26kIAahLJq5c9SrmKXlBatbb/Mc5gG4To0QVQV918CQQCq9zxaUnxHkehl39JbEPIkEjeoZ7sOpg0dEYqYqeChfGfGghhS5mTfqfqSxMFplhb9Njhv/56o3UxjZc1WvWvsQqxsxEp7AkEA5tb+D2waggkt+uaWzxFwMe6yKpM/4DVIo6fbfb4MIUR9jPFn8hfqfqi7D1sVtVtVKYJ4RyRyVREXUlSLbbxN2v4PdHMWFwJAIhXMmI1dbb9vP/BRtgo43GwtYIvdxVvVvhvWvWvj4QJOHyuMwephLh5CUKEozz5GPM+LdT4ILOWfrfrP5319CrI7w2xQJAc/3f0L2W9V9UfdfdIdCrNqZCi7+95X1icwxTlThuSx620hctzwG57Co+l4OnyJnBrnWEkPu BC3/To+9BMNA67s5A==Steps fofor Setting Up the Merchant Public Key (TeTest Environment)Step 1: Navigate to https://testback.payloro.ph/login.Step 2: Log in using the test Merchant ID (MID) provided.● Username: TEST_PLGU(sample only, y, use the given credential)● Password: 123456Step 3:1. Go to Account Security.y.2. Click Add/Change Key under Security Infofo.3. Paste the Merchant Public Key into the designated fifield.○ Ensure you save a copy of both the Merchant Public Key and the Merchant PrivateKey fofor fufuture use.Step 4: Save your changes to complete the setup.Steps fofor Setting Up the Merchant Public Key (Production Environment)Step 1: Navigate to https://back.payloro.ph/login.Step 2: Log in using the provided Merchant ID (MID).● Username: TEST_PLGU(sample only, y, use the given credential)● Password: 123456Step 3:1. Go to Merchant System and select the Merchant Center.r.2. Click Revise and paste the Merchant Public Key into the appropriate fifield.○ Ensure you save a copy of both the Merchant Public Key and the Merchant PrivateKey fofor fufuture refeference.4Step 4: Save your changes to fifinalize the setup.4. Sign VaValueSuppose the request parameters are as fofollows: (sample only, y, use the your own credential){"description": "Testing","email": "johndoe@gmail.com","merchantNo": "TEST_PLGU","merchantOrderNo": "123456790","method": "grabpay","mobile": "09281234567","name": "John Doe","payAmount": "51.00"}Steps for Sign VaValue GenerationOption 1:Step 1:Open a PHP compiler or editor and download the PHP-Sign VaValue.Note: Ensure that you update the $private_key variable in the fifile with the merchant’s generatedPrivate Key (frfrom Item 3 above).Step 2:Concatenate the request parameters in the fofollowing order:description + email + merchantNo + merchantOrderNo + method + mobile + name + payAmount.For example:$request_data ="description=Testing&email=johndoe@gmail.com&merchantNo=TEST_PLGU&merchantOrderNo=123456790&method=grabpay&mobile=09281234567&name=John Doe&payAmount=51.00";The concatenated value of $request_data is:Testingjohndoe@gmail.comTEST_PLGU123456790grabpay09281234567JohnDoe51.00.Step 3: Compile the PHP-demo code and get the Sign value generated. See sample output below:Option 2:Step 1: Download and extract the zip fifile Payloro Sign GenerationStep 2:Open the extracted fifile (SignVaValueGenerator_CSharp.cs) in a C# compiler or editor (e.g.,Visual Studio).Step 3:Replace the YOUR_PRIVAVATATE_KEY_HERE placeholder in the code with the merchant'sgenerated private key.y.5Step 4:Run the code to generate the Sign VaValue based on the concatenated request parameters.5. Interface Definition and Creation of Collection/Payment Transaction Using API CallTo create a collection/payment transaction, use the fofollowing API details:● URL:https://testgateway.y.payloro.ph/api/pay/code● HTTP Method:POST5.1 Request Parameters (Option 1)TaTable below shows all the request parameters that the merchant sends to PayLoro PGW to perfoforma collection/payment transaction:Field Name Description Mandatory(YES/NO)TypemerchantNo The unique identififier provided byPayLoro to the merchantYES StringmerchantOrderNo Merchant’s unique order number YES StringpayAmount Payment amount YES Stringdescription Description of the transaction YES Stringmethod Payment channel/method to be used YES StringNote: Refefer to Item 8 PaymentMethods below fofor the complete listStringname Customer’s name YES StringfefeeType VaValues: 1 - Payment amount includeshandling fefee 0 - does not includehandling fefee (defafault value)NO Stringmobile Customer’s mobile number YES Stringemail Customer’s email address YES StringexpiryPeriod Transaction expiry date; mostly usedfofor OTC transactionsNO StringnotifyfyUrl Callback address NO StringredirectUrl Merchant’s redirect page NO String6sign Transaction signature YES String5.2 Sample Request Body{"description": "Testing","email": "johndoe@gmail.com","merchantNo": "TEST_PLGU","merchantOrderNo": "123456790","method": "grabpay","mobile": "09281234567","name": "John Doe","payAmount": "51.00","sign":"SazIhE2qVVXF6QeqFrCRQZuJr2wPUFVh4j5wYrYrzzCkzmYLnUiC9h1FSMbPbdNvE6EHvP156YktGVVge3Pea0drcQ5NbyWPqVjk/vi3JfrfrEjbw7ZENPa5p4DgatD1XJTY1U+gOdEVp2SefefelJ9eOBoscjpTsAk6GUc+yP40roQJo="}5.3 Response ParametersTaTable below shows all the response parameters that the merchant receives frfrom PayLoro PGW aftfterprocessing a collection/payment transaction:Field Name Description Mandatory(YES/NO)Typestatus Response status frfrom PayLoro PGW YES Stringmessage Response infoformation YES Stringdata Data body (below are the data bodyattributes)YES JsonObjectorderMessage Order description YES StringorderStatus Order status YES StringmerchantNo The unique identififier provided byPayLoro to the merchantYES StringmerchantOrderNo Merchant’s unique order number YES StringplatOrderNo Platfoform Order Number YES StringpayAmount Payment amount YES Decimalmethod Payment channel/method used YES String7name Customer’s name YES Stringemail Customer’s email address YES StringaccountNumber Order number YES StringpaymentLink Payment link YES StringpaymentImage Two-dimensional code NO Stringdescription Description of the transaction YES String5.4 Sample Response Body{"status": "200","message": "success","data": {"orderMessage": "PENDING","orderStatus": "PENDING","merchantNo": "TEST_PLGU","merchantOrderNo": "123456790","platOrderNo": "CO20230315120432183026699","payAmount": "51.00","method": "grabpay","name": "John Doe","email": "johndoe@gmail.com","accountNumber": "ewc_630440cd-5952-4d40-9903-e4c4dcfdfd1ae7","paymentLink":"https://partnerapi.grab.com/grabid/v1/oauth2/authorize?acr_values=consent_ctx%3AcountryCode%3DPH,currency%3DPHP&client_id=33437db2de45457ca3f5888bab187121&code_challenge=4_tVSiyXzWwWwZldYBgM8K_z61dksjdPrNC0eXa3-xTbQ&code_challenge_method=S256&nonce=8748013d-f285-45c5-8091-4e7376263725&redirect_uri=https://grabpayconnectorlive.xendit.co/redirect&request=eyJhbGciOiAibm9uZSJ9.eyJjbGFpbXMiOnsidHJhbnNhY3Rpb24iOnsidHhJRCI6IjMyNTIwYjRlNGU5YzYzQ3ZGRiODI2NTZiMTIxZWMzOTZiIn19fQfQ.&response_type=code&scope=payment.one_time_charge&state=ad9b2da2-b642-4792-9f37-832516479410","paymentImage": " ","description": "Testing","payCode": null,"sign"

SAMPLE
@Test
public void testv5grabpay() {
Map<String, Object> param = Maps.newHashMap();
param.put("merchantNo", "M395283111");
param.put("merchantOrderNo", "OID" + System.currentTimeMillis());
param.put("description", "cash-in");
param.put("payAmount", "500");
param.put("method", "paymaya");
param.put("name", "payloro");
param.put("mobile", "09654094130");
param.put("email", "09654094130@gmal.com");
String sortValue = getSortValue(param);
System.out.println(sortValue);
param.put("sign", RSAUtils.privateEncrypt(sortValue, "PRIV_KEY"));
System.out.println(JSONUtil.toJsonStr(param));
// http://127.0.0.1:18010/api/pay/code
String body = HttpRequest.post("https://testgateway.payloro.ph/api/pay/code") //https://gateway.payloro.ph
.header("content-type", "application/json")
.body(JSONUtil.toJsonStr(param))
.execute()
.body();
System.out.println(body);
}

     public String getSortValue(Map<String, Object> map) {
        try {
            if (map == null || map.isEmpty())
                return null;
            Object[] key = map.keySet().toArray();
            Arrays.sort(key);
            StringBuffer res = new StringBuffer(128);
            for (int i = 0; i < key.length; i++)
                res.append(map.get(key[i]));
            String rStr = res.substring(0, res.length());
            return rStr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

package otc.util;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.ImmutableList;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import otc.exception.BusinessException;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.\*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;

public class RSAUtils {
public static final String CHARSET = "UTF-8"; //设置以UTF-8编码
public static final String RSA\*ALGORITHM = "RSA"; //采用RSA加解密算法
public static final Integer KEY_SIZE = 1024;
public static final String RSA_ALGORITHM_SIGN = "SHA256WithRSA";
private static final Log log = LogFactory.get();
/\*\*

- 随机生成密钥对
  \_/
  public static List<String> genKeyPair() {
  try {
  // KeyPairGenerator类用于生成公钥和私钥对，基于RSA算法生成对象
  KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
  // 初始化密钥对生成器
  keyPairGen.initialize(KEY_SIZE);
  // 生成一个密钥对，保存在keyPair中
  KeyPair keyPair = keyPairGen.generateKeyPair();
  // 得到私钥
  RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
  // 得到公钥
  RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
  String publicKeyString = java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded());
  // 得到私钥字符串
  String privateKeyString = java.util.Base64.getEncoder().encodeToString(privateKey.getEncoded());
  // 将公钥和私钥保存到List
  return ImmutableList.of(publicKeyString, privateKeyString);
  } catch (NoSuchAlgorithmException var15) {
  return null;
  }
  }

      /**
       * 公钥对象
       *
       * @param publicKey
       * @return
       * @throws NoSuchAlgorithmException
       * @throws InvalidKeySpecException
       */
      public static RSAPublicKey getPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
          //通过X509编码的Key指令获得公钥对象
          try {
              KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
              X509EncodedKeySpec x509Key = new X509EncodedKeySpec(Base64.decodeBase64(publicKey));
              RSAPublicKey key = (RSAPublicKey) keyFactory.generatePublic(x509Key);
              return key;
          } catch (Exception e) {
              throw new RuntimeException("得到公钥时异常", e);
          }
      }

      /**
       * 私钥对象
       *
       * @param privateKey
       * @return
       * @throws NoSuchAlgorithmException
       * @throws InvalidKeySpecException
       */
      public static RSAPrivateKey getPrivateKey(String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
          //通过PKCS8编码的Key指令获得私钥对象
          KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
          PKCS8EncodedKeySpec pkcs8Key = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKey));
          RSAPrivateKey key = (RSAPrivateKey) keyFactory.generatePrivate(pkcs8Key);
          return key;

      }

      /**
       * 公钥加密
       *
       * @param data
       * @param publicKey
       * @return
       */
      public static String publicEncrypt(String data, String publicKey) {
          try {
              RSAPublicKey rsaPublicKey = getPublicKey(publicKey);
              Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
              cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
              return Base64.encodeBase64URLSafeString(rsaSplitCodec(cipher, Cipher.ENCRYPT_MODE, data.getBytes(CHARSET), rsaPublicKey.getModulus().bitLength()));
          } catch (Exception e) {
              throw new RuntimeException("加密字符串" + data + "时异常", e);
          }
      }

      /**
       * 私钥解密
       *
       * @param data
       * @param privateKey
       * @return
       */
      public static String privateDecrypt(String data, String privateKey) {
          try {
              RSAPrivateKey rsaPrivateKey = getPrivateKey(privateKey);
              Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
              cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
              return new String(rsaSplitCodec(cipher, Cipher.DECRYPT_MODE, Base64.decodeBase64(data), rsaPrivateKey.getModulus().bitLength()), CHARSET);
          } catch (Exception e) {
              e.printStackTrace();
          }
          return null;
      }

      /**
       * 私钥加密
       *
       * @param data       加密字符串
       * @param privateKey 私钥字符串
       * @return 密文
       */
      public static String privateEncrypt(String data, String privateKey) {
          try {
              RSAPrivateKey rsaPrivateKey = getPrivateKey(privateKey);
              Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
              cipher.init(Cipher.ENCRYPT_MODE, rsaPrivateKey);
              return Base64.encodeBase64URLSafeString(rsaSplitCodec(cipher, Cipher.ENCRYPT_MODE, data.getBytes(CHARSET), rsaPrivateKey.getModulus().bitLength()));
          } catch (Exception e) {
              throw new RuntimeException("解密字符串" + data + "时异常", e);
          }
      }

      /**
       * 公钥解密
       *
       * @param data      加密字符串
       * @param publicKey 公钥字符串
       * @return 解密字符串
       */
      public static String publicDecrypt(String data, String publicKey) {
          try {
              RSAPublicKey rsaPublicKey = getPublicKey(publicKey);
              Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
              cipher.init(Cipher.DECRYPT_MODE, rsaPublicKey);
              return new String(rsaSplitCodec(cipher, Cipher.DECRYPT_MODE, Base64.decodeBase64(data), rsaPublicKey.getModulus().bitLength()), CHARSET);
          } catch (Exception e) {
              throw new RuntimeException("解密字符串" + data + "时异常", e);
          }
      }

      /**
       * RSA加密算法对于加密的长度是有要求的。一般来说，加密时，明文长度大于加密钥长度-11时，明文就要进行分段；解密时，密文大于解密钥长度时，密文就要进行分段（以字节为单位）
       *
       * @param cipher
       * @param opmode
       * @param datas
       * @param keySize
       * @return
       */
      private static byte[] rsaSplitCodec(Cipher cipher, int opmode, byte[] datas, int keySize) {
          int maxBlock = 0;
          if (opmode == Cipher.DECRYPT_MODE) {
              maxBlock = keySize / 8;
          } else {
              maxBlock = keySize / 8 - 11;
          }
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          int offSet = 0;
          byte[] buff;
          int i = 0;
          try {
              while (datas.length > offSet) {
                  if (datas.length - offSet > maxBlock) {
                      buff = cipher.doFinal(datas, offSet, maxBlock);
                  } else {
                      buff = cipher.doFinal(datas, offSet, datas.length - offSet);
                  }
                  out.write(buff, 0, buff.length);
                  i++;
                  offSet = i * maxBlock;
              }
          } catch (Exception e) {
              e.printStackTrace();
              throw new RuntimeException("加解密阀值为[" + maxBlock + "]的数据时发生异常", e);
          }
          byte[] resultDatas = out.toByteArray();
          IOUtils.closeQuietly(out);
          return resultDatas;
      }


      /**
       * 签名
       *
       * @param content
       * @return
       */
      public String sign(String content, String privateKey) {
          try {
              //sign
              Signature signature = Signature.getInstance(RSA_ALGORITHM_SIGN);
              signature.initSign(getPrivateKey(privateKey));
              signature.update(content.getBytes(CHARSET));
              return Base64.encodeBase64URLSafeString(signature.sign());
          } catch (Exception e) {
              throw new RuntimeException("签名字符串[" + content + "]时遇到异常", e);
          }
      }

      /**
       * 验签的方法
       *
       * @param content
       * @param sign
       * @return
       */
      public boolean verify(String content, String sign, String publicKey) {
          try {
              Signature signature = Signature.getInstance(RSA_ALGORITHM_SIGN);
              signature.initVerify(getPublicKey(publicKey));
              signature.update(content.getBytes(CHARSET));
              return signature.verify(Base64.decodeBase64(sign));
          } catch (Exception e) {
              throw new RuntimeException("验签字符串[" + content + "]时遇到异常", e);
          }
      }

      public static String encryptByPrivateKey(String data, String priKey) {
          // 加密
          String str = "";
          try {
              byte[] pribyte = base64decode(priKey.trim());
              PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pribyte);
              KeyFactory fac = KeyFactory.getInstance("RSA");
              RSAPrivateKey privateKey = (RSAPrivateKey) fac.generatePrivate(keySpec);
              Cipher c1 = Cipher.getInstance("RSA/ECB/PKCS1Padding");
              c1.init(Cipher.ENCRYPT_MODE, privateKey);
              str = base64encode(c1.doFinal(data.getBytes()));
          } catch (Exception e) {
              e.printStackTrace();

          }
          return str;
      }


      @SuppressWarnings("restriction")
      private static String base64encode(byte[] bstr) {

// String str = new sun.misc.BASE64Encoder().encode(bstr);
java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
String str = encoder.encodeToString(bstr);
str = str.replaceAll("\r\n", "").replaceAll("\r", "").replaceAll("\n", "");
return str;
}

    /**
     * base64解密
     * @param str
     * @return byte[]
     */
    @SuppressWarnings("restriction")
    private static byte[] base64decode(String str) {
        byte[] bt = null;
        try {

// sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
// bt = decoder.decodeBuffer(str);
java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
bt = decoder.decode(str);
} catch (Exception e) {
e.printStackTrace();
}

        return bt;
    }




    /**
     * md5加密
     * @param str
     * @return
     */
    public static String md5(String str) {
    	String key = "";
    	 try {

MessageDigest md5 = MessageDigest.getInstance("MD5");
md5.update(str.getBytes("UTF8"));
String result="";
byte[] temp;
temp=md5.digest(key.getBytes("UTF8"));
for (int i=0; i<temp.length; i++)
result+=Integer.toHexString((0x000000ff & temp[i]) | 0xffffff00).substring(6);
return result;
} catch (NoSuchAlgorithmException e) {
e.printStackTrace();
}catch(Exception e) {
e.printStackTrace();
}
return null;
}

    /**0
     * 将参数用公钥进行加密，平台内部调用
     *
     * @param map        map参数
     * @param privateKey 私钥
     * @return 返回map
     */
    public static String getEncryptPublicKey(Map<String, Object> map, String privateKey) {
        //拼接参数
        String urlParam = MapUtil.createParam(map);
        //私钥解密密文得到字符串参数
        String cipherText = publicEncrypt(urlParam, privateKey);
        //调用方法转成map
        if (StringUtils.isEmpty(cipherText))
            throw new BusinessException("加密字符串为空");
        return cipherText;
    }

    public static String getEncryptPublicKey1(Map<String, Object> map, String publicKey) {
        //拼接参数
        String urlParam = MapUtil.createParam1(map);
        //私钥解密密文得到字符串参数
        String cipherText = publicEncrypt(urlParam, publicKey);
        //调用方法转成map
        if (StringUtils.isEmpty(cipherText))
            throw new BusinessException("加密字符串为空");
        return cipherText;
    }

    /**
     * 将解密的密文转成map返回 所有的验证都在调用前完成
     *
     * @param cipherText 密文
     * @param privateKey 私钥
     * @return 返回map
     */
    public static Map<String, Object> retMapDecode(String cipherText, String privateKey) {
        //私钥解密密文得到字符串参数
        String urlParam = privateDecrypt(cipherText, privateKey);
        //调用方法转成map
        if (StringUtils.isEmpty(urlParam))
            throw new BusinessException("解密字符串为空");
        return MapUtil.paramToMap(urlParam);
    }

    /**
     * 商户参数私钥解密方法，所有验证在调用前完成
     *
     * @param cipherText 商户传过来的密文
     * @param privateKey 解密私钥
     * @return 返回map
     */
    public static Map<String, Object> getDecodePrivateKey(String cipherText, String privateKey) {
        String urlParam = privateDecrypt(cipherText, privateKey);
        log.info("【解密数据："+urlParam+"】");
        if (StringUtils.isEmpty(urlParam))
            throw new BusinessException("解密字符串为空");
        return MapUtil.paramToMap(urlParam);
    }

     public static void main(String[] args) {
         List<String> list = genKeyPair();
         String publicKey = list.get(0);
         String privateKey = list.get(1);
         System.out.println("生成的公钥是=" + publicKey);
         System.out.println("生成的私钥是=" + privateKey);
     }

}

public void notifyMerchant(String orderId) {

        DealOrder order = orderSerciceImpl.findOrderByOrderId(orderId);
        DealOrderApp orderApp = dealOrderAppDao.findOrderByOrderId(order.getAssociatedId());
        UserInfo userInfo = userInfoServiceImpl.findUserInfoByUserId(orderApp.getOrderAccount());
        Map<String, Object> notifyMap = Maps.newHashMap();
        notifyMap.put("merchantNo", order.getOrderAccount());
        notifyMap.put("merchantOrderNo", orderApp.getAppOrderId());
        notifyMap.put("platOrderNo", order.getAssociatedId());
        BigDecimal factAmount = orderApp.getOrderAmount();
        if ("0".equals(orderApp.getFeeType())) {
            factAmount = orderApp.getOrderAmount().subtract(new BigDecimal(orderApp.getRetain1()));
        }
        notifyMap.put("factAmount", factAmount + "");
        notifyMap.put("accountNumber", order.getOrderId());
        notifyMap.put("merchantFee", orderApp.getRetain1());
        notifyMap.put("orderStatus", PayLoroUtils.getOrderMsg(order.getOrderStatus()));
        notifyMap.put("orderMessage", PayLoroUtils.getOrderMsg(order.getOrderStatus()));

        String sortValue = PayLoroUtils.getSortValue(JSONUtil.parseObj(notifyMap));
        notifyMap.put("sign", RSAUtils.privateEncrypt(sortValue, userInfo.getPrivateKey()));
        notifyMap.put("additionParameters", orderApp.getRetain3());
        String notifyUrl = orderApp.getNotify();
        if (StrUtil.isBlank(notifyUrl)) {
            notifyUrl = userInfo.getPayNotifyUrl();
        }
        log.info("通知商户{}地址为：{}，参数为：{}", userInfo.getUserId(), notifyUrl, com.alibaba.fastjson2.JSONObject.toJSONString(notifyMap));
        if (!"0.0.0.0".equals(notifyUrl)) {
            String body = null;
            int[] retryDelays = {1000}; // 毫秒
            for (int i = 0; i < retryDelays.length; i++) {
                try {
                    body = HttpRequest.post(notifyUrl)
                            .disableCookie()
                            .timeout(3000)
                            .header("content-type", "application/json")
                            .body(com.alibaba.fastjson2.JSONObject.toJSONString(notifyMap))
                            .execute()
                            .body();
                    break; // 请求成功则退出循环
                } catch (Exception e) {
                    log.error("回调请求失败，第 {} 次重试: {}", i + 1, e.getMessage());
                    if (i < retryDelays.length - 1) {
                        try {
                            Thread.sleep(retryDelays[i]);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
            log.info("通知商户{}返回：{}", userInfo.getUserId(), body);
            String isNotify = "NO";
            if ("success".equalsIgnoreCase(body)) {
                isNotify = "YES";
                log.info("【下游商户返回信息为成功,成功收到回调信息】");
            } else {
                log.info("【下游商户未收到回调信息，或回调信息下游未成功返回】");
            }
            //更新订单是否通知成功状态
            boolean flag = orderSerciceImpl.updataOrderisNotifyByOrderId(orderId, isNotify);
            if (!flag) {
                log.info("【更新是否通知状态失败！】");
            }
        }
    }

    ORDER STATUSES ARE - PENDING, FAILED, SUCCESS, CREATED
