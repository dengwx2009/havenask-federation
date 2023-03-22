/*
*Copyright (c) 2021, Alibaba Group;
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

*   http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright Havenask Contributors. See
 * GitHub history for details.
 */

package org.havenask.common.settings;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.havenask.cli.ExitCodes;
import org.havenask.cli.Terminal;
import org.havenask.cli.UserException;
import org.havenask.env.Environment;

import java.util.List;

/**
 * A subcommand for the keystore cli to remove a setting.
 */
class RemoveSettingKeyStoreCommand extends BaseKeyStoreCommand {

    private final OptionSpec<String> arguments;

    RemoveSettingKeyStoreCommand() {
        super("Remove settings from the keystore", true);
        arguments = parser.nonOptions("setting names");
    }

    @Override
    protected void executeCommand(Terminal terminal, OptionSet options, Environment env) throws Exception {
        List<String> settings = arguments.values(options);
        if (settings.isEmpty()) {
            throw new UserException(ExitCodes.USAGE, "Must supply at least one setting to remove");
        }
        final KeyStoreWrapper keyStore = getKeyStore();
        for (String setting : arguments.values(options)) {
            if (keyStore.getSettingNames().contains(setting) == false) {
                throw new UserException(ExitCodes.CONFIG, "Setting [" + setting + "] does not exist in the keystore.");
            }
            keyStore.remove(setting);
        }
        keyStore.save(env.configFile(), getKeyStorePassword().getChars());
    }
}
