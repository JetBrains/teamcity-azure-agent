/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.serverSide.crypt.RSACipher;
import org.apache.commons.codec.DecoderException;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 10/20/2014
 *         Time: 4:03 PM
 */

@Test
public class TestDeserialize {

  public void testDeserialize() throws IOException, DecoderException {

    String jsondata = "{\"data\":\"beta\"}";
    Gson gson = new Gson();
    Map<String, String> map = new HashMap<String, String>();
    map = gson.fromJson(jsondata, map.getClass());
    System.out.println();

    final String data =
      "aa7214cd622cf7e6fa086bd0b4e7bb0fcc61b320901f97c17d77c9f7e0df30b0f60bbb19fe72e5a93fda4aa6288bc0f3b1ed2a4badf985ac6677edf0f58d31d415c70d46163c6843a245fd4c90ff65fb381c916d2476c0759ca0f87316bc6b95ec4be4054a114f86ab16297850c6665b9548057b770167aaebe71f0a916146a6";
    final String pw = "qwerty";
    final String s = RSACipher.decryptData(RSACipher.encryptData(pw));
    System.out.println(s);
    System.out.println(data);
    System.out.println(RSACipher.encryptData(pw));

    System.out.println(RSACipher.getHexEncodedPublicKey());
    System.out.println(RSACipher.encryptDataForWeb(pw));
  }

}
