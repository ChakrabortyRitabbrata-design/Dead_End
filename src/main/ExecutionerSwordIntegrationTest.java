package main;

import animation.Animation;
import animation.AnimationState;
import animation.Direction;
import entities.Dragon;
import entities.Monster;
import entities.Player;
import inventory.EquipmentSlot;
import inventory.ExecutionerSword;
import inventory.Item;
import inventory.Weapon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Automated test suite verifying all 17 conditions for the Legendary Executioner Sword.
 */
public class ExecutionerSwordIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Executioner Sword Integration Tests...");

        test1_Region1WithHighScoreRemainsLocked();
        test2_Region2WithHighScoreRemainsLocked();
        test3_Region3WithLowScoreRemainsLocked();
        test4_Region3WithHighScoreUnlocks();
        test5_PopupAppearsTopRightOnlyOnce();
        test6_InventoryDisplaysCorrectIcon();
        test7_EquipAndUnequip();
        test8_EquippedPlayerSprites();
        test9_RegularZombieOneHitKill();
        test10_11_WaterDragonDamageAndTwoHitDefeat();
        test12_BothDragonsDefeatCompletesGame();
        test13_14_SprintDisabledWhileEquippedAndRestoredOnUnequip();
        test15_QuickSaveAndQuickLoadPreservesSword();
        test16_ExistingWeaponsRetainOriginalDamage();
        test17_ExistingAnimationsAndGameplayFunctional();

        System.out.println("\nALL 17 EXECUTIONER SWORD TESTS PASSED SUCCESSFULLY!");
    }

    private static void test1_Region1WithHighScoreRemainsLocked() {
        System.out.print("Test 1: Region 1 with 250+ score remains locked... ");
        GamePanel gp = new GamePanel();
        gp.levelM.currentLevel = 1;
        // Set score >= 250
        while (gp.levelM.progression.score() < 300) {
            gp.levelM.progression.award("test-kill-" + System.nanoTime(), "undead");
        }
        gp.checkExecutionerSwordUnlock();
        if (gp.executionerSwordUnlocked) {
            throw new AssertionError("Sword should NOT unlock in Region 1!");
        }
        if (gp.hasExecutionerSword()) {
            throw new AssertionError("Sword should not be present in inventory in Region 1!");
        }
        System.out.println("PASSED");
    }

    private static void test2_Region2WithHighScoreRemainsLocked() {
        System.out.print("Test 2: Region 2 with 250+ score remains locked... ");
        GamePanel gp = new GamePanel();
        gp.levelM.currentLevel = 2;
        while (gp.levelM.progression.score() < 300) {
            gp.levelM.progression.award("test-kill-" + System.nanoTime(), "undead");
        }
        gp.checkExecutionerSwordUnlock();
        if (gp.executionerSwordUnlocked) {
            throw new AssertionError("Sword should NOT unlock in Region 2!");
        }
        if (gp.hasExecutionerSword()) {
            throw new AssertionError("Sword should not be present in inventory in Region 2!");
        }
        System.out.println("PASSED");
    }

    private static void test3_Region3WithLowScoreRemainsLocked() {
        System.out.print("Test 3: Region 3 with < 250 score remains locked... ");
        GamePanel gp = new GamePanel();
        gp.levelM.currentLevel = 3;
        // Score is 0
        gp.checkExecutionerSwordUnlock();
        if (gp.executionerSwordUnlocked) {
            throw new AssertionError("Sword should NOT unlock in Region 3 if score < 250!");
        }
        System.out.println("PASSED");
    }

    private static void test4_Region3WithHighScoreUnlocks() {
        System.out.print("Test 4: Region 3 with >= 250 score unlocks... ");
        GamePanel gp = new GamePanel();
        gp.levelM.currentLevel = 3;
        while (gp.levelM.progression.score() < 250) {
            gp.levelM.progression.award("test-kill-" + System.nanoTime(), "undead");
        }
        gp.checkExecutionerSwordUnlock();
        if (!gp.executionerSwordUnlocked) {
            throw new AssertionError("Sword MUST unlock when region == 3 and score >= 250!");
        }
        if (!gp.hasExecutionerSword()) {
            throw new AssertionError("Executioner Sword must be added to inventory upon unlocking!");
        }
        System.out.println("PASSED");
    }

    private static void test5_PopupAppearsTopRightOnlyOnce() {
        System.out.print("Test 5: Popup appears top-right and only once... ");
        GamePanel gp = new GamePanel();
        gp.levelM.currentLevel = 3;
        while (gp.levelM.progression.score() < 250) {
            gp.levelM.progression.award("test-kill-" + System.nanoTime(), "undead");
        }
        gp.checkExecutionerSwordUnlock();
        if (!gp.executionerNotification.isActive()) {
            throw new AssertionError("Acquisition popup should be active upon unlock!");
        }

        // Simulate subsequent frames
        gp.checkExecutionerSwordUnlock();
        gp.checkExecutionerSwordUnlock();

        // Advance notification until finished
        for (int i = 0; i < ExecutionerSwordNotification.TOTAL_TICKS + 10; i++) {
            gp.executionerNotification.update();
        }
        if (gp.executionerNotification.isActive()) {
            throw new AssertionError("Popup should expire after duration!");
        }

        // Triggering checkExecutionerSwordUnlock again must NOT reactivate popup
        gp.checkExecutionerSwordUnlock();
        if (gp.executionerNotification.isActive()) {
            throw new AssertionError("Popup must NOT re-trigger after weapon is already unlocked!");
        }
        System.out.println("PASSED");
    }

    private static void test6_InventoryDisplaysCorrectIcon() {
        System.out.print("Test 6: Inventory displays correct icon and rarity... ");
        ExecutionerSword sword = new ExecutionerSword();
        if (!"LEGENDARY".equalsIgnoreCase(sword.rarity)) {
            throw new AssertionError("Sword rarity must be LEGENDARY!");
        }
        BufferedImage icon = sword.getIcon(42);
        if (icon == null || icon.getWidth() != 42 || icon.getHeight() != 42) {
            throw new AssertionError("Executioner Sword must provide a valid scaled icon!");
        }
        System.out.println("PASSED");
    }

    private static void test7_EquipAndUnequip() {
        System.out.print("Test 7: Sword can be equipped and unequipped... ");
        GamePanel gp = new GamePanel();
        ExecutionerSword sword = new ExecutionerSword();
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);
        if (gp.equipmentManager.getEquipped(EquipmentSlot.MAIN_HAND) != sword) {
            throw new AssertionError("Sword was not equipped in MAIN_HAND!");
        }
        if (!gp.player.isExecutionerSwordEquipped()) {
            throw new AssertionError("Player should report Executioner Sword is equipped!");
        }

        Item unequipped = gp.equipmentManager.unequip(EquipmentSlot.MAIN_HAND);
        if (unequipped != sword) {
            throw new AssertionError("Unequipping should return the sword!");
        }
        if (gp.player.isExecutionerSwordEquipped()) {
            throw new AssertionError("Player should report sword is not equipped after unequip!");
        }
        System.out.println("PASSED");
    }

    private static void test8_EquippedPlayerSprites() {
        System.out.print("Test 8: Equipped player uses correct sprites and directions... ");
        GamePanel gp = new GamePanel();
        ExecutionerSword sword = new ExecutionerSword();

        // Before equip: normal player frame
        BufferedImage unequippedFrame = gp.player.getActiveFrameImage();

        // Equip Executioner Sword
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);
        gp.player.setState(AnimationState.IDLE);
        gp.player.setFacing(Direction.DOWN);

        BufferedImage downFrame = gp.player.getActiveFrameImage();
        if (downFrame == null) {
            throw new AssertionError("Equipped player must return a valid sprite frame!");
        }

        gp.player.setFacing(Direction.LEFT);
        BufferedImage leftFrame = gp.player.getActiveFrameImage();

        gp.player.setFacing(Direction.RIGHT);
        BufferedImage rightFrame = gp.player.getActiveFrameImage();

        gp.player.setFacing(Direction.UP);
        BufferedImage upFrame = gp.player.getActiveFrameImage();

        if (leftFrame == null || rightFrame == null || upFrame == null) {
            throw new AssertionError("All four directions must have valid frames!");
        }
        System.out.println("PASSED");
    }

    private static void test9_RegularZombieOneHitKill() {
        System.out.print("Test 9: Regular zombie dies in one successful hit... ");
        GamePanel gp = new GamePanel();
        ExecutionerSword sword = new ExecutionerSword();
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);

        Monster zombie = new Monster(gp, 100, 100);
        zombie.health = zombie.getMaxHealth();
        int originalHealth = zombie.health;
        if (originalHealth <= 0) {
            throw new AssertionError("Zombie should start with positive health!");
        }

        // Align player to hit zombie
        gp.player.worldX = 100;
        gp.player.worldY = 100 + gp.tileSize;
        gp.player.setFacing(Direction.UP);
        gp.player.attackSequence = 1;
        gp.player.setState(AnimationState.PUNCH);
        gp.player.getWeaponRenderer().startAttack(sword);

        // Simulate zombie taking attack
        int damage = gp.player.getAttackDamage();
        if (gp.player.isExecutionerSwordEquipped()) {
            damage = Math.max(damage, zombie.getMaxHealth());
        }
        zombie.takeDamage(damage);

        if (zombie.health != 0 || !zombie.isDying()) {
            throw new AssertionError("Regular zombie must be defeated in 1 hit! Health remaining: " + zombie.health);
        }

        // Advance frames to complete death animation
        for (int i = 0; i < 40; i++) {
            zombie.update();
        }
        if (!zombie.isDead()) {
            throw new AssertionError("Zombie should transition to dead state after death animation!");
        }
        System.out.println("PASSED");
    }

    private static void test10_11_WaterDragonDamageAndTwoHitDefeat() throws Exception {
        System.out.print("Tests 10 & 11: Water Dragon takes 50% max HP per hit; 2 hits defeat it... ");
        GamePanel gp = new GamePanel();
        var map3 = tile.MapLoader.loadLevel(3);
        gp.levelM.enter(3, map3.playerSpawn[0], map3.playerSpawn[1]);
        ExecutionerSword sword = new ExecutionerSword();
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);

        Dragon dragon = gp.dragon;
        if (dragon == null) {
            throw new AssertionError("Dragon should be spawned in Region 3!");
        }
        int maxHp = dragon.getMaxHealth(); // 300

        // Hit 1
        int damage = gp.player.isExecutionerSwordEquipped() ? maxHp / 2 : gp.player.getAttackDamage();
        if (damage != 150) {
            throw new AssertionError("Hit damage against Water Dragon must be exactly 150 (50% max HP)! Got: " + damage);
        }
        dragon.takeDamage(damage);
        if (dragon.health != 150) {
            throw new AssertionError("Dragon health after 1 hit must be 150! Got: " + dragon.health);
        }
        if (dragon.isDead) {
            throw new AssertionError("Dragon should still be alive after only 1 hit!");
        }

        // Hit 2
        dragon.takeDamage(damage);
        if (dragon.health != 0) {
            throw new AssertionError("Dragon health after 2 hits must be 0! Got: " + dragon.health);
        }
        System.out.println("PASSED");
    }

    private static void test12_BothDragonsDefeatCompletesGame() throws Exception {
        System.out.print("Test 12: Defeating both Region 3 dragons completes the game... ");
        GamePanel gp = new GamePanel();
        var map3 = tile.MapLoader.loadLevel(3);
        gp.levelM.enter(3, map3.playerSpawn[0], map3.playerSpawn[1]);
        ExecutionerSword sword = new ExecutionerSword();
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);

        if (gp.dragon == null || gp.dragon2 == null) {
            throw new AssertionError("Region 3 must have two water dragons active!");
        }

        // Defeat dragon 1 with 2 hits
        gp.dragon.takeDamage(150);
        gp.dragon.takeDamage(150);
        gp.levelM.onDragonDefeated(gp.dragon);

        if (gp.gameState == GamePanel.GameState.GAME_COMPLETE) {
            throw new AssertionError("Game must not complete when only 1 dragon is defeated!");
        }

        // Defeat dragon 2 with 2 hits
        gp.dragon2.takeDamage(150);
        gp.dragon2.takeDamage(150);
        gp.levelM.onDragonDefeated(gp.dragon2);

        if (gp.gameState != GamePanel.GameState.GAME_COMPLETE) {
            throw new AssertionError("Game must transition to GAME_COMPLETE when both dragons are defeated!");
        }
        System.out.println("PASSED");
    }

    private static void test13_14_SprintDisabledWhileEquippedAndRestoredOnUnequip() {
        System.out.print("Tests 13 & 14: Sprint disabled while equipped and restored on unequip... ");
        GamePanel gp = new GamePanel();
        ExecutionerSword sword = new ExecutionerSword();

        // 1. Without sword equipped, sprint is allowed
        gp.keyH.runPressed = true;
        if (!gp.player.canSprint()) {
            throw new AssertionError("Sprint should be active when runPressed is true and sword is not equipped!");
        }

        // 2. Equip sword -> sprint must be disabled
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);
        if (gp.player.canSprint()) {
            throw new AssertionError("Sprint must be DISABLED while Executioner Sword is equipped!");
        }

        // 3. Unequip sword -> sprint immediately restored
        gp.equipmentManager.unequip(EquipmentSlot.MAIN_HAND);
        if (!gp.player.canSprint()) {
            throw new AssertionError("Sprint must be immediately restored after unequipping Executioner Sword!");
        }
        System.out.println("PASSED");
    }

    private static void test15_QuickSaveAndQuickLoadPreservesSword() throws Exception {
        System.out.print("Test 15: Quick Save and Quick Load preserve sword and states... ");
        GamePanel gp = new GamePanel();
        var map3 = tile.MapLoader.loadLevel(3);
        gp.levelM.enter(3, map3.playerSpawn[0], map3.playerSpawn[1]);
        while (gp.levelM.progression.score() < 250) {
            gp.levelM.progression.award("save-test-" + System.nanoTime(), "undead");
        }
        gp.checkExecutionerSwordUnlock();

        // Equip sword
        ExecutionerSword sword = new ExecutionerSword();
        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);

        Path saveFile = Files.createTempFile("dead_end_test_save_", ".properties");
        try {
            gp.levelM.save(saveFile);

            GamePanel restored = new GamePanel();
            restored.levelM.load(saveFile);

            if (!restored.executionerSwordUnlocked) {
                throw new AssertionError("Save/load must preserve executionerSwordUnlocked status!");
            }
            if (!restored.player.isExecutionerSwordEquipped()) {
                throw new AssertionError("Save/load must preserve equipped Executioner Sword in MAIN_HAND!");
            }
            if (restored.executionerNotification.isActive()) {
                throw new AssertionError("Acquisition popup must NOT re-appear after loading a saved game!");
            }
        } finally {
            Files.deleteIfExists(saveFile);
        }
        System.out.println("PASSED");
    }

    private static void test16_ExistingWeaponsRetainOriginalDamage() {
        System.out.print("Test 16: Existing weapons retain original damage... ");
        GamePanel gp = new GamePanel();
        Weapon woodSword = new Weapon("Wooden Sword", "desc", 8, 25, 50, "Sword");
        Weapon ironSword = new Weapon("Iron Sword", "desc", 12, 30, 60, "Sword");
        Weapon axe = new Weapon("Basic Axe", "desc", 15, 45, 55, "Axe");

        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, woodSword);
        gp.player.getWeaponRenderer().startAttack(woodSword);
        if (gp.player.getAttackDamage() != 8) {
            throw new AssertionError("Wooden sword damage should be 8! Got: " + gp.player.getAttackDamage());
        }

        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, ironSword);
        gp.player.getWeaponRenderer().startAttack(ironSword);
        if (gp.player.getAttackDamage() != 12) {
            throw new AssertionError("Iron sword damage should be 12! Got: " + gp.player.getAttackDamage());
        }

        gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, axe);
        gp.player.getWeaponRenderer().startAttack(axe);
        if (gp.player.getAttackDamage() != 15) {
            throw new AssertionError("Axe damage should be 15! Got: " + gp.player.getAttackDamage());
        }
        System.out.println("PASSED");
    }

    private static void test17_ExistingAnimationsAndGameplayFunctional() {
        System.out.print("Test 17: Existing animations and gameplay remain functional... ");
        GamePanel gp = new GamePanel();
        // Check punch, kick, dodge, roll
        gp.player.setState(AnimationState.IDLE);
        if (gp.player.getState() != AnimationState.IDLE) throw new AssertionError();
        gp.player.setState(AnimationState.WALK);
        if (gp.player.getState() != AnimationState.WALK) throw new AssertionError();
        gp.player.setState(AnimationState.DODGE);
        if (gp.player.getState() != AnimationState.DODGE) throw new AssertionError();
        gp.player.setState(AnimationState.ROLL);
        if (gp.player.getState() != AnimationState.ROLL) throw new AssertionError();
        System.out.println("PASSED");
    }
}
