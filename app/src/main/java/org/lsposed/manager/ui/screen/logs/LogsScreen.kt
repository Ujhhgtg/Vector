/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.screen.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lsposed.manager.R

/** Fragment-scoped side effects for the logs screen. */
class LogsActions(
    val save: () -> Unit,
    val clear: (verbose: Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    wordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    prettyPrint: Boolean,
    onPrettyPrintChange: (Boolean) -> Unit,
    actions: LogsActions,
) {
    val tabs = listOf(
        stringResource(R.string.nav_item_logs_module) to false,
        stringResource(R.string.nav_item_logs_verbose) to true,
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    // One LazyListState per page so scroll-to-top/bottom can target the visible tab.
    val listStates = listOf(rememberLazyListState(), rememberLazyListState())
    val subtitle = if (viewModel.verboseLogEnabled) {
        stringResource(R.string.enabled_verbose_log)
    } else {
        stringResource(R.string.disabled_verbose_log)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tapping the title scrolls the visible tab to the top, mirroring the legacy
                    // LogsFragment toolbar click gesture.
                    Column(
                        modifier = Modifier.clickable {
                            scope.launch { listStates[pagerState.currentPage].animateScrollToItem(0) }
                        },
                    ) {
                        Text(stringResource(R.string.Logs))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.save) {
                        Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.menuSaveToSd))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    LogsOverflowMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        wordWrap = wordWrap,
                        onWordWrapChange = onWordWrapChange,
                        prettyPrint = prettyPrint,
                        onPrettyPrintChange = onPrettyPrintChange,
                        onScrollTop = {
                            scope.launch { listStates[pagerState.currentPage].animateScrollToItem(0) }
                        },
                        onScrollBottom = {
                            scope.launch {
                                val end = (listStates[pagerState.currentPage].layoutInfo.totalItemsCount - 1)
                                    .coerceAtLeast(0)
                                listStates[pagerState.currentPage].animateScrollToItem(end)
                            }
                        },
                        onClear = { actions.clear(tabs[pagerState.currentPage].second) },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                // Pager swipe only when word-wrap (or pretty-print) is on; otherwise horizontal text
                // scroll owns the gesture.
                userScrollEnabled = wordWrap || prettyPrint,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val verbose = tabs[page].second
                val state by (if (verbose) viewModel.verbose else viewModel.module)
                    .collectAsStateWithLifecycle()
                LogPage(
                    state = state,
                    wordWrap = wordWrap,
                    prettyPrint = prettyPrint,
                    listState = listStates[page],
                    onRefresh = { viewModel.load(verbose) },
                )
            }
        }
    }
}

@Composable
private fun LogsOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    wordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    prettyPrint: Boolean,
    onPrettyPrintChange: (Boolean) -> Unit,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onClear: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scroll_top)) },
            onClick = {
                onDismiss()
                onScrollTop()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scroll_bottom)) },
            onClick = {
                onDismiss()
                onScrollBottom()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menuClearLog)) },
            onClick = {
                onDismiss()
                onClear()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_enable_word_wrap)) },
            trailingIcon = {
                androidx.compose.material3.Checkbox(checked = wordWrap, onCheckedChange = null)
            },
            onClick = {
                onDismiss()
                onWordWrapChange(!wordWrap)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_pretty_print)) },
            trailingIcon = {
                androidx.compose.material3.Checkbox(checked = prettyPrint, onCheckedChange = null)
            },
            onClick = {
                onDismiss()
                onPrettyPrintChange(!prettyPrint)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogPage(
    state: LogTabState,
    wordWrap: Boolean,
    prettyPrint: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading && state.lines.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.lines.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        stringResource(R.string.list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            prettyPrint -> PrettyLogContent(state.lines, listState)
            else -> LogContent(state.lines, wordWrap, listState)
        }
    }
}

@Composable
private fun LogContent(
    lines: List<String>,
    wordWrap: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    // In unwrap mode every row shares one horizontal ScrollState so the whole log scrolls together,
    // mirroring the legacy HorizontalScrollView wrapping the RecyclerView. Applying the scroll
    // per-row (not to the LazyColumn) avoids the unsupported infinite-width lazy-layout measurement.
    val hScroll = rememberScrollState()
    SelectionContainer {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lines.size) { index ->
                val rowModifier = if (wordWrap) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.horizontalScroll(hScroll)
                }
                LogLine(lines[index], wordWrap = wordWrap, modifier = rowModifier)
            }
        }
    }
}

@Composable
private fun LogLine(line: String, wordWrap: Boolean, modifier: Modifier) {
    Text(
        text = line,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 12.sp,
        softWrap = wordWrap,
        maxLines = if (wordWrap) Int.MAX_VALUE else 1,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Pretty-printing mode: each logcat line is parsed into a structured [LogEntry] and rendered as a
 * card with a colored level chip, the metadata (time / pid-tid / tag) as a header, and the message
 * body word-wrapped below. Unparseable lines (e.g. stack-trace continuations) attach to the
 * preceding card as extra body lines so multi-line entries stay together.
 */
@Composable
private fun PrettyLogContent(
    lines: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val entries = remember(lines) { parseLogEntries(lines) }
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries.size) { index -> LogEntryCard(entries[index]) }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val levelColor = levelColor(entry.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.level != null) {
                    Surface(
                        color = levelColor,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = entry.level.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (entry.tag != null) {
                        Text(
                            text = entry.tag,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    val meta = listOfNotNull(entry.time, entry.pidTid).joinToString("  ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (entry.message.isNotEmpty()) {
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = entry.message,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** A logcat threadtime level color (chip background). */
@Composable
private fun levelColor(level: Char?): Color = when (level) {
    'E', 'F' -> MaterialTheme.colorScheme.error
    'W' -> Color(0xFFF57C00)
    'I' -> Color(0xFF388E3C)
    'D' -> Color(0xFF1976D2)
    'V' -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.secondary
}

/** A parsed Vector log entry; metadata fields are null for lines that don't match the header format. */
private data class LogEntry(
    val time: String?,
    val pidTid: String?,
    val level: Char?,
    val tag: String?,
    val message: String,
)

// Vector's logcat writer (daemon/.../logcat.cpp FastWrite) emits, per entry:
//   "[ " + "yyyy-MM-dd'T'HH:mm:ss" + ".mmm %8d:%6d:%6d %c/%-15s ] " + message
// e.g. "[ 2026-06-30T16:43:11.414     1000:  3155:  3155 I/VectorLegacyBridge ] Hook entry"
// Groups: 1=time(date+time+ms) 2=uid 3=pid 4=tid 5=level 6=tag 7=message
private val VECTOR_LOG_REGEX = Regex(
    """^\[ (\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+):\s*(\d+):\s*(\d+)\s+([VDIWEFAS])/(.+?)\s*] (.*)$""",
)

// Part markers the writer interleaves between FD rotations, e.g. "----part 1 start----".
private val PART_MARKER_REGEX = Regex("""^-+part \d+ (start|end)-+$""")

/**
 * Parses raw log lines into [LogEntry] cards. A line matching the Vector header starts a new card;
 * any line that doesn't (stack-trace continuations, multi-line messages) is appended to the previous
 * card so multi-line entries stay together. Part markers and leading orphan lines become their own
 * metadata-less cards.
 */
private fun parseLogEntries(lines: List<String>): List<LogEntry> {
    val out = ArrayList<LogEntry>()
    for (line in lines) {
        val m = VECTOR_LOG_REGEX.find(line)
        when {
            m != null -> {
                val (time, _, pid, tid, level, tag, msg) = m.destructured
                // time is "yyyy-MM-ddTHH:mm:ss.mmm"; show only the wall-clock time + ms for brevity.
                val shortTime = time.substringAfter('T')
                out.add(
                    LogEntry(
                        time = shortTime,
                        pidTid = "$pid-$tid",
                        level = level.firstOrNull(),
                        tag = tag.trim(),
                        message = msg,
                    ),
                )
            }
            out.isNotEmpty() && !PART_MARKER_REGEX.matches(line) -> {
                val prev = out.removeAt(out.size - 1)
                out.add(prev.copy(message = if (prev.message.isEmpty()) line else prev.message + "\n" + line))
            }
            else -> {
                out.add(LogEntry(time = null, pidTid = null, level = null, tag = null, message = line))
            }
        }
    }
    return out
}
