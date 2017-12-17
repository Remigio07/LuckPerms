/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.impl.log;

import me.lucko.luckperms.common.actionlog.ExtendedLogEntry;
import me.lucko.luckperms.common.actionlog.Log;
import me.lucko.luckperms.common.commands.CommandException;
import me.lucko.luckperms.common.commands.CommandResult;
import me.lucko.luckperms.common.commands.abstraction.SubCommand;
import me.lucko.luckperms.common.commands.sender.Sender;
import me.lucko.luckperms.common.commands.utils.CommandUtils;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.constants.CommandPermission;
import me.lucko.luckperms.common.constants.DataConstraints;
import me.lucko.luckperms.common.locale.CommandSpec;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.utils.DateUtil;
import me.lucko.luckperms.common.utils.Predicates;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

public class LogRecent extends SubCommand<Log> {
    private static final int ENTRIES_PER_PAGE = 10;
    
    public LogRecent(LocaleManager locale) {
        super(CommandSpec.LOG_RECENT.spec(locale), "recent", CommandPermission.LOG_RECENT, Predicates.notInRange(0, 2));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, Log log, List<String> args, String label) throws CommandException {
        if (args.size() == 0) {
            // No page or user
            return showLog(log.getRecentMaxPages(ENTRIES_PER_PAGE), null, sender, log);
        }

        if (args.size() == 1) {
            // Page or user
            try {
                int p = Integer.parseInt(args.get(0));
                // page
                return showLog(p, null, sender, log);
            } catch (NumberFormatException ignored) {
            }
        }

        // User and possibly page
        final String target = args.get(0);
        UUID uuid = CommandUtils.parseUuid(target.toLowerCase());
        if (uuid == null) {
            if (!plugin.getConfiguration().get(ConfigKeys.ALLOW_INVALID_USERNAMES)) {
                if (!DataConstraints.PLAYER_USERNAME_TEST.test(target)) {
                    Message.USER_INVALID_ENTRY.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            } else {
                if (!DataConstraints.PLAYER_USERNAME_TEST_LENIENT.test(target)) {
                    Message.USER_INVALID_ENTRY.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            }

            uuid = plugin.getStorage().getUUID(target.toLowerCase()).join();
            if (uuid == null) {
                if (!plugin.getConfiguration().get(ConfigKeys.USE_SERVER_UUID_CACHE)) {
                    Message.USER_NOT_FOUND.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }

                uuid = plugin.lookupUuid(target).orElse(null);
                if (uuid == null) {
                    Message.USER_NOT_FOUND.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            }
        }

        if (args.size() != 2) {
            // Just user
            return showLog(log.getRecentMaxPages(uuid, ENTRIES_PER_PAGE), uuid, sender, log);
        } else {
            try {
                int p = Integer.parseInt(args.get(1));
                // User and page
                return showLog(p, uuid, sender, log);
            } catch (NumberFormatException e) {
                // Invalid page
                return showLog(-1, null, sender, log);
            }
        }
    }

    private static CommandResult showLog(int page, UUID filter, Sender sender, Log log) {
        int maxPage = (filter != null) ? log.getRecentMaxPages(filter, ENTRIES_PER_PAGE) : log.getRecentMaxPages(ENTRIES_PER_PAGE);
        if (maxPage == 0) {
            Message.LOG_NO_ENTRIES.send(sender);
            return CommandResult.STATE_ERROR;
        }

        if (page < 1 || page > maxPage) {
            Message.LOG_INVALID_PAGE_RANGE.send(sender, maxPage);
            return CommandResult.INVALID_ARGS;
        }

        SortedMap<Integer, ExtendedLogEntry> entries = (filter != null) ? log.getRecent(page, filter, ENTRIES_PER_PAGE) : log.getRecent(page, ENTRIES_PER_PAGE);
        if (filter != null) {
            String name = entries.values().stream().findAny().get().getActorName();
            if (name.contains("@")) {
                name = name.split("@")[0];
            }
            Message.LOG_RECENT_BY_HEADER.send(sender, name, page, maxPage);
        } else {
            Message.LOG_RECENT_HEADER.send(sender, page, maxPage);
        }

        long now = DateUtil.unixSecondsNow();
        for (Map.Entry<Integer, ExtendedLogEntry> e : entries.entrySet()) {
            long time = e.getValue().getTimestamp();
            Message.LOG_ENTRY.send(sender,
                    e.getKey(),
                    DateUtil.formatTimeBrief(now - time),
                    e.getValue().getActorFriendlyString(),
                    Character.toString(e.getValue().getType().getCode()),
                    e.getValue().getActedFriendlyString(),
                    e.getValue().getAction()
            );
        }
        return CommandResult.SUCCESS;
    }
}
