/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.util.StringUtil;

/**
 * Default error message updater.
 */
public class DefaultErrorMessageUpdater implements ErrorMessageUpdater {
    @Override
    public String getFriendlyErrorMessage(String message) {
        return message;
    }

    @Override
    public String getFriendlyErrorMessage(String message, String defaultMessage) {
        if (StringUtil.isEmpty(message)) {
            return "No details available";
        }

        return defaultMessage;
    }

    @Override
    public String getFriendlyErrorMessage(Throwable th) {
        final String message = th.getMessage();
        return getFriendlyErrorMessage(message, message);
    }

    @Override
    public String getFriendlyErrorMessage(Throwable th, String defaultMessage) {
        return getFriendlyErrorMessage(th.getMessage(), defaultMessage);
    }
}
