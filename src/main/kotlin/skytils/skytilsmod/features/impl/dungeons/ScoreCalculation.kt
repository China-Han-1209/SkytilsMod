/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2022 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package skytils.skytilsmod.features.impl.dungeons

import gg.essential.universal.UResolution
import net.minecraft.client.Minecraft
import net.minecraft.entity.monster.EntityZombie
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import skytils.skytilsmod.Skytils
import skytils.skytilsmod.core.SoundQueue
import skytils.skytilsmod.core.structure.FloatPair
import skytils.skytilsmod.core.structure.GuiElement
import skytils.skytilsmod.events.impl.PacketEvent.ReceiveEvent
import skytils.skytilsmod.features.impl.handlers.MayorInfo
import skytils.skytilsmod.utils.*
import skytils.skytilsmod.utils.graphics.ScreenRenderer
import skytils.skytilsmod.utils.graphics.SmartFontRenderer
import skytils.skytilsmod.utils.graphics.SmartFontRenderer.TextAlignment
import skytils.skytilsmod.utils.graphics.colors.CommonColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.regex.Pattern
import kotlin.math.*

object ScoreCalculation {

    private val partyAssistSecretsPattern =
        Regex("^Party > .+: \\\$SKYTILS-DUNGEON-SCORE-ROOM\\$: \\[(?<name>.+)] \\((?<secrets>\\d+)\\)$")!!
    private val mc = Minecraft.getMinecraft()
    private var ticks = 0

    private val deathsTabPattern = Regex("§r§a§lDeaths: §r§f\\((?<deaths>\\d+)\\)§r")
    private val puzzlesTabPattern = Regex("§r§b§lPuzzles: §r§f\\((?<puzzles>\\d+)\\)§r")
    private val missingPuzzlePattern = Regex("§r (?<puzzle>.+): §r§7\\[§r§6§l✦§r§7] ?§r")
    private val failedPuzzlePattern =
        Regex("§r (?<puzzle>.+): §r§7\\[§r§c§l✖§r§7] §r§f(?:\\((?:§r(?<player>.+))?§r§f\\)|\\(§r§f})§r")
    private val secretsFoundPattern = Regex("§r Secrets Found: §r§b(?<secrets>\\d+)§r")
    private val secretsFoundPercentagePattern = Regex("§r Secrets Found: §r§[ae](?<percentage>[\\d.]+)%§r")
    private val cryptsPattern = Regex("§r Crypts: §r§6(?<crypts>\\d+)§r")
    private val dungeonClearedPattern = Regex("Dungeon Cleared: (?<percentage>\\d+)%")
    private val timeElapsedPattern =
        Regex("Time Elapsed: (?:(?<hrs>\\d+)h )?(?:(?<min>\\d+)m )?(?:(?<sec>\\d+)s)?")
    private val roomCompletedPattern = Regex("§r Completed Rooms: §r§d(?<count>\\d+)§r")

    val floorRequirements = hashMapOf(
        // idk what entrance is so i put it as the same as f1
        "E" to FloorRequirement(.3),
        "F1" to FloorRequirement(.3),
        "F2" to FloorRequirement(.4),
        "F3" to FloorRequirement(.5),
        "F4" to FloorRequirement(.6, 12 * 60),
        "F5" to FloorRequirement(.7),
        "F6" to FloorRequirement(.85),
        "F7" to FloorRequirement(speed = 12 * 60),
        "M1" to FloorRequirement(speed = 8 * 60),
        "M2" to FloorRequirement(speed = 8 * 60),
        "M3" to FloorRequirement(speed = 8 * 60),
        "M4" to FloorRequirement(speed = 8 * 60),
        "M5" to FloorRequirement(speed = 8 * 60),
        "M6" to FloorRequirement(speed = 8 * 60),
        //still waiting on m7 release lol
        "M7" to FloorRequirement(speed = 8 * 60),
        "default" to FloorRequirement()
    )

    var deaths = 0
    var puzzles = 0
    var missingPuzzles = 0
    var failedPuzzles = 0
    var foundSecrets = 0
    var completedRooms = 0
    var crypts = 0
    var secretsFoundPercentage = 0.000
    var speedScoreCancelled = 0
    var secondsCancelled = 6
    var mimicKilled = false
    var bloodDoor = false
    var firstDeathHadSpirit = false
    var clearedPercentage = 0
    var secondsElapsed = 0.0
    var isPaul = false
    var skillScore = 0
    var percentageSecretsFound = 0.0
    var discoveryScore = 0
    var speedScore = 0
    var bonusScore = 0
    var perRoomPercentage = 0.0
    var sent270Message = false
    var sent300Message = false

    var floorReq = floorRequirements["default"]!!

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (mc.thePlayer != null && mc.theWorld != null && Utils.inDungeons) {
            if (Skytils.config.showScoreCalculation && ticks % 5 == 0) {
                missingPuzzles = 0
                failedPuzzles = 0
                for (line in ScoreboardUtil.sidebarLines) {
                    if (line.startsWith("Dungeon Cleared:")) {
                        val matcher = dungeonClearedPattern.find(line)
                        if (matcher != null) {
                            clearedPercentage = matcher.groups["percentage"]?.value?.toIntOrNull() ?: 0
                            continue
                        }
                    }
                    if (line.startsWith("Time Elapsed:")) {
                        val matcher = timeElapsedPattern.find(line)
                        if (matcher != null) {
                            val hours = matcher.groups["hrs"]?.value?.toIntOrNull() ?: 0
                            val minutes = matcher.groups["min"]?.value?.toIntOrNull() ?: 0
                            val seconds = matcher.groups["sec"]?.value?.toIntOrNull() ?: 0
                            secondsElapsed = (hours * 3600 + minutes * 60 + seconds).toDouble()
                            continue
                        }
                    }
                }
                for ((_, name) in TabListUtils.tabEntries) {
                    try {
                        when {
                            name.contains("Deaths:") -> {
                                val matcher = deathsTabPattern.find(name) ?: continue
                                deaths = matcher.groups["deaths"]?.value?.toIntOrNull() ?: 0
                            }
                            name.contains("Puzzles:") -> {
                                val matcher = puzzlesTabPattern.find(name) ?: continue
                                puzzles = matcher.groups["puzzles"]?.value?.toIntOrNull() ?: 0
                            }
                            name.contains("✦") -> {
                                if (missingPuzzlePattern.containsMatchIn(name)) {
                                    missingPuzzles++
                                }
                            }
                            name.contains("✖") -> {
                                if (failedPuzzlePattern.containsMatchIn(name)) {
                                    failedPuzzles++
                                }
                            }
                            name.contains("Secrets Found:") -> {
                                if (name.contains("%")) {
                                    val matcher = secretsFoundPercentagePattern.find(name) ?: continue
                                    secretsFoundPercentage = matcher.groups["percentage"]?.value?.toDoubleOrNull() ?: 0.0
                                } else {
                                    val matcher = secretsFoundPattern.find(name) ?: continue
                                    foundSecrets = matcher.groups["secrets"]?.value?.toIntOrNull() ?: 0
                                }
                            }
                            name.contains("Crypts:") -> {
                                val matcher = cryptsPattern.find(name) ?: continue
                                crypts = matcher.groups["crypts"]?.value?.toIntOrNull() ?: 0
                            }
                            name.contains("Completed Rooms") -> {
                                val matcher = roomCompletedPattern.find(name) ?: continue
                                completedRooms = matcher.groups["count"]?.value?.toIntOrNull() ?: continue
                                if (completedRooms > 0) {
                                    perRoomPercentage = (clearedPercentage / completedRooms.toDouble())
                                }
                            }
                        }
                    } catch (ignored: NumberFormatException) {
                    }
                }

                val totalRoom = if(clearedPercentage > 0) floor(100f / clearedPercentage * completedRooms + 0.5).toInt() else 0
                val roomClear = if (bloodDoor && !DungeonFeatures.hasBossSpawned) completedRooms + 1 else completedRooms
                val trueClearPercentage = if (totalRoom > 0) min((roomClear.toDouble() / totalRoom.toDouble()), 100.0) else 0.0
                val puzzlePercentage = (puzzles - missingPuzzles - failedPuzzles).toDouble() / puzzles.toDouble()
                val totalSkill = 20 + floor(60 * trueClearPercentage).toInt() + floor(20 * puzzlePercentage).toInt()
                val skillScore = max(0, totalSkill - (2 * deaths - if (firstDeathHadSpirit) 1 else 0))
                val calcTotalSecrets = if (foundSecrets > 0 && secretsFoundPercentage > 0) floor(100f / secretsFoundPercentage * foundSecrets + 0.5) else 0.0
                val secretFoundRequirementPercentage = secretsFoundPercentage / floorReq.secretPercentage / 100f
                val secretFoundRequirement = ceil(floorReq.secretPercentage * calcTotalSecrets).toInt()
                val roomClearScore = floor(
                    (60 * trueClearPercentage).toDouble().coerceIn(0.0, 60.0)
                )
                val secretScore = if (calcTotalSecrets <= 0) 0.0 else floor(
                    (40f * secretFoundRequirementPercentage).coerceIn(0.0, 40.0)
                )
                val discoveryScore = roomClearScore + secretScore
                val bonusScore = (if (mimicKilled) 2 else 0) + crypts.coerceAtMost(5) + if (isPaul) 10 else 0
                val countedSeconds = secondsElapsed
                if(countedSeconds.toInt() - secondsCancelled >= floorReq.speed && speedScoreCancelled < 100){
                    speedScoreCancelled++
                    secondsCancelled += (speedScoreCancelled / 10 + 1) * 6
                }
                val speedScore = max(0, 100 - speedScoreCancelled)
                val score = (skillScore + discoveryScore + speedScore + bonusScore).toInt()
                val rank = if (score < 100) "§cD" else if (score < 160) "§9C" else if (score < 230) "§aB" else if (score < 270) "§5A" else if (score < 300) "§eS" else "§6S+"
                if (Skytils.config.sendMessageOn270Score && !sent270Message && score >= 270) {
                    sent270Message = true
                    Skytils.sendMessageQueue.add("Skytils > 270 score")
                }
                if (Skytils.config.sendMessageOn300Score && !sent300Message && score >= 300) {
                    sent300Message = true
                    Skytils.sendMessageQueue.add("Skytils > 300 score")
                }

                ScoreCalculationElement.text.clear()
                if (Skytils.config.minimizedScoreCalculation) {
                    val color = when {
                        score < 270 -> 'c'
                        score < 300 -> 'e'
                        else -> 'a'
                    }
                    ScoreCalculationElement.text.add("§6Score: §$color$score")
                } else {
                    ScoreCalculationElement.text.add("§9Dungeon Status")
                    ScoreCalculationElement.text.add("§f• §eDeaths:§c $deaths")
                    ScoreCalculationElement.text.add("§f• §eMissing Puzzles:§c $missingPuzzles")
                    ScoreCalculationElement.text.add("§f• §eFailed Puzzles:§c $failedPuzzles")
                    if (discoveryScore > 0) ScoreCalculationElement.text.add("§f• §eSecrets:${if (foundSecrets >= secretFoundRequirement) "§a" else "§c"} $foundSecrets§7/§a${secretFoundRequirement} §7(§6Total: ${calcTotalSecrets.toInt()}§7)")
                    ScoreCalculationElement.text.add("§f• §eCrypts:§a $crypts")
                    if (Utils.equalsOneOf(DungeonFeatures.dungeonFloor, "F6", "F7", "M6", "M7")) {
                        ScoreCalculationElement.text.add("§f• §eMimic:" + if (mimicKilled) "§a ✓" else " §c X")
                    }
                    ScoreCalculationElement.text.add("")
                    ScoreCalculationElement.text.add("§6Score:")
                    ScoreCalculationElement.text.add("§f• §eSkill Score:§a $skillScore")
                    ScoreCalculationElement.text.add("§f• §eExplore Score:§a " + discoveryScore.toInt() + " §7(§e${roomClearScore.toInt()} §7+ §6${secretScore.toInt()}§7)")
                    ScoreCalculationElement.text.add("§f• §eSpeed Score:§a " + speedScore.toInt())
                    ScoreCalculationElement.text.add("§f• §eBonus Score:§a $bonusScore")
                    ScoreCalculationElement.text.add("§f• §eTotal Score:§a $score" + if(isPaul) " §7(§6+10§7)" else "")
                    ScoreCalculationElement.text.add("§f• §eRank: $rank")
                }
                ticks = 0
            }
        }
        ticks++
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    fun onChatReceived(event: ClientChatReceivedEvent) {
        if (!Utils.inDungeons || mc.thePlayer == null) return
        val unformatted = event.message.unformattedText.stripControlCodes()
        if(unformatted.contains("The BLOOD DOOR has been opened!")){
            bloodDoor = true
        }
        if (Skytils.config.scoreCalculationReceiveAssist) {
            if (unformatted.startsWith("Party > ")) {
                if (unformatted.contains("\$SKYTILS-DUNGEON-SCORE-MIMIC$") || (Skytils.config.receiveHelpFromOtherModMimicDead && unformatted.containsAny(
                        "Mimic dead!", "Mimic Killed!", "Mimic Dead!"
                    ))
                ) {
                    mimicKilled = true
                    return
                }
                if (unformatted.contains("\$SKYTILS-DUNGEON-SCORE-ROOM$")) {
                    if (partyAssistSecretsPattern.containsMatchIn(unformatted)) {
                        event.isCanceled = true
                        return
                    }
                }
            }
        }
        if (Skytils.config.removePartyChatNotifFromScoreCalc) {
            if (unformatted.startsWith("Party > ") && mc.thePlayer != null && !unformatted.contains(mc.thePlayer.name)) {
                SoundQueue.addToQueue("random.orb", 1f)
            }
        }
    }

    @SubscribeEvent
    fun onEntityDeath(event: LivingDeathEvent) {
        if (!Utils.inDungeons) return
        if (event.entity is EntityZombie) {
            val entity = event.entity as EntityZombie
            if (entity.isChild && entity.getCurrentArmor(0) == null && entity.getCurrentArmor(1) == null && entity.getCurrentArmor(
                    2
                ) == null && entity.getCurrentArmor(3) == null
            ) {
                if (!mimicKilled) {
                    mimicKilled = true
                    if (Skytils.config.scoreCalculationAssist) {
                        Skytils.sendMessageQueue.add("/pc \$SKYTILS-DUNGEON-SCORE-MIMIC$")
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onReceivePacket(event: ReceiveEvent) {
        if (!Utils.inDungeons) return
        if (event.packet is S29PacketSoundEffect) {
            val packet = event.packet
            val sound = packet.soundName
            val pitch = packet.pitch
            val volume = packet.volume
            if (Skytils.config.removePartyChatNotifFromScoreCalc && sound == "random.orb" && pitch == 1f && volume == 1f) {
                event.isCanceled = true
            }
        }
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Load) {
        mimicKilled = false
        bloodDoor = false
        speedScoreCancelled = 0
        secondsCancelled = 6
        firstDeathHadSpirit = false
        floorReq = floorRequirements["default"]!!
        perRoomPercentage = 0.0
        sent270Message = false
        sent300Message = false
    }

    init {
        ScoreCalculationElement()
        HugeCryptsCounter()
    }

    class HugeCryptsCounter : GuiElement("Dungeon Crypts Counter", 2f, FloatPair(200, 200)) {
        override fun render() {
            if (toggled && Utils.inDungeons && DungeonTimer.dungeonStartTime != -1L) {
                val sr = UResolution
                val leftAlign = actualX < sr.scaledWidth / 2f
                ScreenRenderer.fontRenderer.drawString(
                    "Crypts: $crypts",
                    if (leftAlign) 0f else width.toFloat(),
                    0f,
                    alignment = if (leftAlign) TextAlignment.LEFT_RIGHT else TextAlignment.RIGHT_LEFT,
                    customColor = if (crypts < 5) CommonColors.RED else CommonColors.LIGHT_GREEN
                )
            }
        }

        override fun demoRender() {
            val sr = UResolution
            val leftAlign = actualX < sr.scaledWidth / 2f
            ScreenRenderer.fontRenderer.drawString(
                "Crypts: 5",
                if (leftAlign) 0f else width.toFloat(),
                0f,
                alignment = if (leftAlign) TextAlignment.LEFT_RIGHT else TextAlignment.RIGHT_LEFT,
                customColor = CommonColors.LIGHT_GREEN
            )
        }

        override val toggled: Boolean
            get() = Skytils.config.bigCryptsCounter
        override val height: Int
            get() = fr.FONT_HEIGHT
        override val width: Int
            get() = fr.getStringWidth("Crypts: 5")

        init {
            Skytils.guiManager.registerElement(this)
        }
    }

    class ScoreCalculationElement : GuiElement("Dungeon Score Estimate", FloatPair(200, 100)) {
        override fun render() {
            if (toggled && Utils.inDungeons) {
                RenderUtil.drawAllInList(this, text)
            }
        }

        override fun demoRender() {
            RenderUtil.drawAllInList(this, demoText)
        }


        companion object {
            private val demoText = listOf(
                "§9Dungeon Status",
                "§f• §eDeaths:§c 0",
                "§f• §eMissing Puzzles:§c 0",
                "§f• §eFailed Puzzles:§c 0",
                "§f• §eSecrets: §a50§7/§a50 §7(§6Total: 50§7)",
                "§f• §eCrypts:§a 5",
                "§f• §eMimic: §a ✓",
                "",
                "§6Score:",
                "§f• §eSkill Score:§a 100",
                "§f• §eExplore Score:§a 100 §7(§e60 §7+ §640§7)",
                "§f• §eSpeed Score:§a 100",
                "§f• §eBonus Score:§a 17",
                "§f• §eTotal Score:§a 317 §7(§6+10§7)",
                "§f• §eRank: §6S+"
            )
            val text = ArrayList<String>()
        }

        override val height: Int
            get() = ScreenRenderer.fontRenderer.FONT_HEIGHT * 4
        override val width: Int
            get() = ScreenRenderer.fontRenderer.getStringWidth("§6Estimated Secret Count: 99")

        override val toggled: Boolean
            get() = Skytils.config.showScoreCalculation

        init {
            Skytils.guiManager.registerElement(this)
        }
    }

    data class FloorRequirement(val secretPercentage: Double = 1.0, val speed: Int = 10 * 60)
}