package yio.tro.antiyoy.gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import yio.tro.antiyoy.*;
import yio.tro.antiyoy.factor_yio.FactorYio;
import yio.tro.antiyoy.gameplay.data_storage.EncodeableYio;
import yio.tro.antiyoy.gameplay.diplomacy.DiplomacyManager;
import yio.tro.antiyoy.gameplay.diplomacy.DiplomaticEntity;
import yio.tro.antiyoy.gameplay.editor.EditorProvinceData;
import yio.tro.antiyoy.gameplay.fog_of_war.FogOfWarManager;
import yio.tro.antiyoy.gameplay.game_view.GameView;
import yio.tro.antiyoy.gameplay.rules.GameRules;
import yio.tro.antiyoy.menu.scenes.Scenes;
import yio.tro.antiyoy.stuff.GraphicsYio;
import yio.tro.antiyoy.stuff.PointYio;
import yio.tro.antiyoy.stuff.Yio;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

public class FieldManager implements EncodeableYio{

    public final GameController gameController;
    public boolean letsCheckAnimHexes;
    public float hexSize;
    public float hexStep1;
    public float hexStep2;
    public Hex field[][];
    public ArrayList<Hex> activeHexes;
    public ArrayList<Hex> selectedHexes;
    public ArrayList<Hex> animHexes;
    public int fWidth;
    public int fHeight;
    public PointYio fieldPos;
    public float cos60;
    public float sin60;
    public Hex focusedHex;
    public Hex nullHex;
    public Hex responseAnimHex;
    public Hex defTipHex;
    public ArrayList<Hex> solidObjects;
    public ArrayList<Hex> defenseTips;
    public FactorYio responseAnimFactor;
    public FactorYio defenseTipFactor;
    public ArrayList<Province> provinces;
    public Province selectedProvince;
    public long timeToCheckAnimHexes;
    public int[] playerHexCount;
    public float compensatoryOffset; // fix for widescreen
    public FogOfWarManager fogOfWarManager;
    public DiplomacyManager diplomacyManager;
    public String initialLevelString;
    public MoveZoneManager moveZoneManager;
    private ArrayList<Hex> tempList;
    private ArrayList<Hex> propagationList;
    public MassMarchManager massMarchManager;
    public AutomaticTransitionWorker automaticTransitionWorker;


    public FieldManager(GameController gameController) {
        this.gameController = gameController;

        cos60 = (float) Math.cos(Math.PI / 3d);
        sin60 = (float) Math.sin(Math.PI / 3d);
        fieldPos = new PointYio();
        compensatoryOffset = 0;
        updateFieldPos();
        hexSize = 0.05f * Gdx.graphics.getWidth(); // radius
        hexStep1 = (float) Math.sqrt(3) * hexSize; // height
        hexStep2 = (float) Yio.distance(0, 0, 1.5 * hexSize, 0.5 * hexStep1);
        fWidth = 85;
        fHeight = 55;
        activeHexes = new ArrayList<>();
        selectedHexes = new ArrayList<>();
        animHexes = new ArrayList<>();
        solidObjects = new ArrayList<>();
        moveZoneManager = new MoveZoneManager(this);
        field = new Hex[fWidth][fHeight];
        responseAnimFactor = new FactorYio();
        provinces = new ArrayList<>();
        nullHex = new Hex(-1, -1, new PointYio(), this);
        nullHex.active = false;
        defenseTipFactor = new FactorYio();
        defenseTips = new ArrayList<>();
        fogOfWarManager = new FogOfWarManager(this);
        diplomacyManager = new DiplomacyManager(this);
        initialLevelString = null;
        tempList = new ArrayList<>();
        propagationList = new ArrayList<>();
        massMarchManager = new MassMarchManager(this);
        automaticTransitionWorker = new AutomaticTransitionWorker(this);
    }


    private void updateFieldPos() {
        fieldPos.y = -1.1f * GraphicsYio.height + compensatoryOffset;
    }


    public void updateHexInsideLevelStatuses() {
        for (int i = 0; i < fWidth; i++) {
            for (int j = 0; j < fHeight; j++) {
                field[i][j].updateCanContainsObjects();
            }
        }
    }


    public void clearField() {
        gameController.selectionManager.setSelectedUnit(null);
        solidObjects.clear();
        gameController.getUnitList().clear();
        clearProvincesList();
        moveZoneManager.clear();
        clearActiveHexesList();
    }


    public void cleanOutAllHexesInField() {
        for (int i = 0; i < fWidth; i++) {
            for (int j = 0; j < fHeight; j++) {
                Hex hex = gameController.fieldManager.field[i][j];
                // inactive hexes are cleaned too: a ship may be standing on water
                if (!hex.active && !hex.containsUnit()) continue;
                gameController.cleanOutHex(hex);
            }
        }
    }


    public void clearProvincesList() {
        provinces.clear();
    }


    public void defaultValues() {
        selectedProvince = null;
        moveZoneManager.defaultValues();
        compensatoryOffset = 0;
    }


    public void clearActiveHexesList() {
        ListIterator listIterator = activeHexes.listIterator();
        while (listIterator.hasNext()) {
            listIterator.next();
            listIterator.remove();
        }
    }


    public void createField() {
        clearField();
        updateFieldPos();
    }


    public void generateMap() {
        generateMap(GameRules.slayRules);
    }


    public void generateMap(boolean slayRules) {
        if (slayRules) {
            gameController.getMapGeneratorSlay().generateMap(gameController.getPredictableRandom(), field);
        } else {
            gameController.getMapGeneratorGeneric().generateMap(gameController.getPredictableRandom(), field);
        }

        detectProvinces();
        gameController.selectionManager.deselectAll();
        detectNeutralLands();
        gameController.takeAwaySomeMoneyToAchieveBalance();
    }


    public void detectNeutralLands() {
        if (GameRules.slayRules) return;

        for (Hex activeHex : activeHexes) {
            activeHex.genFlag = false;
        }

        for (Province province : provinces) {
            for (Hex hex : province.hexList) {
                hex.genFlag = true;
            }
        }

        for (Hex activeHex : activeHexes) {
            if (activeHex.genFlag) continue;

            activeHex.setFraction(GameRules.NEUTRAL_FRACTION);
        }
    }


    public int[] getIncomeArray() {
        int[] array = new int[GameRules.fractionsQuantity];

        for (int i = 0; i < array.length; i++) {
            array[i] = 0;
        }

        for (Province province : gameController.fieldManager.provinces) {
            int fraction = province.getFraction();
            if (fraction >= array.length) continue;
            array[fraction] += province.getIncome();
        }

        return array;
    }


    public void killUnitByStarvation(Hex hex) {
        cleanOutHex(hex);
        addSolidObject(hex, Obj.GRAVE);
        hex.animFactor.appear(1, 2);

        gameController.replayManager.onUnitDiedFromStarvation(hex);
    }


    public void killEveryoneByStarvation(Province province) {
        for (Hex hex : province.hexList) {
            if (hex.containsUnit()) {
                killUnitByStarvation(hex);
            }
        }

        // ships at sea are fed by this province too - bankruptcy reaches them across the water
        ArrayList<Unit> unitList = gameController.getUnitList();
        for (int i = unitList.size() - 1; i >= 0; i--) {
            Unit unit = unitList.get(i);
            if (!unit.ship || unit.currentHex.active) continue;
            if (unit.getOriginProvince() != province) continue;
            sinkShip(unit);
        }
    }


    /**
     * A ship dying at sea leaves no grave - it just sinks, and the water hex it borrowed
     * a fraction from goes back to being nobody's sea.
     */
    public void sinkShip(Unit unit) {
        Hex hex = unit.currentHex;
        removeUnitFromHex(hex);
        hex.fraction = GameRules.NEUTRAL_FRACTION;
    }


    public void moveResponseAnimHex() {
        if (responseAnimHex != null) {
            responseAnimFactor.move();
            if (responseAnimFactor.get() < 0.01) responseAnimHex = null;
        }
    }


    public void move() {
        moveAnimHexes();
        automaticTransitionWorker.move();
    }


    private void moveAnimHexes() {
        for (Hex hex : animHexes) {
            if (!hex.selected) hex.move(); // to prevent double call of move()
            if (!letsCheckAnimHexes && hex.animFactor.get() > 0.99) {
                letsCheckAnimHexes = true;
            }

            // animation is off because it's buggy
            if (hex.animFactor.get() < 1) hex.animFactor.setValues(1, 0);
        }
    }


    public boolean isThereOnlyOneKingdomOnMap() {
        // kingdom can be multiple provinces of same fraction
        int fraction = -1;
        for (Province province : provinces) {
            if (province.hexList.get(0).isNeutral()) continue;

            if (fraction == -1) {
                fraction = province.getFraction();
                continue;
            }

            if (province.getFraction() != fraction) {
                return false;
            }
        }

        return true;
    }


    public int numberOfDifferentActiveProvinces() {
        int c = 0;
        for (Province province : provinces) {
            if (province.hexList.get(0).isNeutral()) continue;
            c++;
        }
        return c;
    }


    public int[] getPlayerHexCount() {
        for (int i = 0; i < playerHexCount.length; i++) {
            playerHexCount[i] = 0;
        }

        for (Hex activeHex : activeHexes) {
            if (activeHex.isNeutral()) continue;
            if (activeHex.isInProvince() && activeHex.fraction >= 0 && activeHex.fraction < playerHexCount.length) {
                playerHexCount[activeHex.fraction]++;
            }
        }

        return playerHexCount;
    }


    public int getLevelSize() {
        return gameController.levelSizeManager.levelSize;
    }


    private boolean checkRefuseStatistics() {
        RefuseStatistics instance = RefuseStatistics.getInstance();

        int sum = instance.refusedEarlyGameEnd + instance.acceptedEarlyGameEnd;
        if (sum < 5) return true;

        double ratio = (double) instance.acceptedEarlyGameEnd / (double) sum;

        if (ratio < 0.1) return false;

        return true;
    }


    public int possibleWinner() {
        if (!checkRefuseStatistics()) return -1;

        int numberOfAllHexes = activeHexes.size();

        int playerHexCount[] = getPlayerHexCount();
        for (int i = 0; i < playerHexCount.length; i++) {
            if (playerHexCount[i] > 0.7 * numberOfAllHexes) {
                return i;
            }
        }

        return -1;
    }


    public boolean hasAtLeastOneProvince() {
        return provinces.size() > 0;
    }


    public int numberOfProvincesWithFraction(int fraction) {
        int count = 0;
        for (Province province : provinces) {
            if (province.getFraction() != fraction) continue;
            count++;
        }
        return count;
    }


    public void transformGraves() {
        for (Hex hex : activeHexes) {
            if (gameController.isCurrentTurn(hex.fraction) && hex.objectInside == Obj.GRAVE) {
                spawnTree(hex);
                hex.blockToTreeFromExpanding = true;
            }
        }
    }


    public void detectProvinces() {
        if (gameController.isInEditorMode()) return;

        clearProvincesList();
        MoveZoneDetection.unFlagAllHexesInArrayList(activeHexes);
        tempList.clear();
        propagationList.clear();

        ArrayList<ArrayList<Hex>> overseasComponents = new ArrayList<>();
        for (Hex hex : activeHexes) {
            if (hex.isNeutral()) continue;
            if (hex.flag) continue;

            tempList.clear();
            propagationList.clear();
            propagationList.add(hex);
            hex.flag = true;
            propagateHex(tempList, propagationList);
            // an overseas colony is not a province of its own (whatever its size) - it lives
            // off a mainland province and is attached to one below
            if (isOverseasComponent(tempList)) {
                overseasComponents.add(new ArrayList<>(tempList));
                continue;
            }
            if (tempList.size() >= 2) {
                Province province = new Province(gameController, tempList);
                addProvince(province);
            }
        }

        for (Province province : provinces) {
            if (province.hasCapital()) continue;

            province.placeCapitalInRandomPlace(gameController.predictableRandom);
        }

        attachOverseasComponents(overseasComponents);
    }


    /**
     * Re-attaches overseas colonies to a mainland province of their fraction after provinces
     * were rebuilt from scratch. The largest province is chosen as the host; when the snapshot
     * knows better (undo), LevelSnapshot corrects the ownership afterwards. A colony whose
     * fraction has no mainland province left collapses.
     */
    private void attachOverseasComponents(ArrayList<ArrayList<Hex>> overseasComponents) {
        for (ArrayList<Hex> component : overseasComponents) {
            Province host = getMaxProvinceWithFraction(component.get(0).fraction);
            if (host == null) {
                makeColonyIndependent(component);
                continue;
            }

            for (Hex hex : component) {
                host.addHex(hex);
            }
        }
    }


    /**
     * A colony outlives its motherland. When the mainland province it was living off is gone, the
     * beachhead stops being a colony and stands on its own: an ordinary province with its own
     * capital, which can build and defend itself like any other. It inherits no money - the treasury
     * died with the mainland - so it starts from nothing and lives on what its own hexes earn.
     * <p>
     * A single hex cannot be a province anywhere in this game, so a lone survivor still collapses.
     */
    private void makeColonyIndependent(ArrayList<Hex> component) {
        for (Hex hex : component) {
            hex.overseasPart = false;
        }

        if (component.size() < 2) {
            destroyBuildingsOnHex(component.get(0));
            return;
        }

        Province province = new Province(gameController, component);
        province.money = 0;
        if (!province.hasCapital()) {
            province.placeCapitalInRandomPlace(gameController.getPredictableRandom());
        }
        addProvince(province);
    }


    private Province getMaxProvinceWithFraction(int fraction) {
        Province max = null;
        for (Province province : provinces) {
            if (province.getFraction() != fraction) continue;
            if (max == null || province.hexList.size() > max.hexList.size()) max = province;
        }
        return max;
    }


    public void tryToDetectAdditionalProvinces() {
        // this method doesn't erase already existing provinces, it just adds new ones

        if (gameController.isInEditorMode()) return;

        MoveZoneDetection.unFlagAllHexesInArrayList(activeHexes);
        tempList.clear();
        propagationList.clear();

        for (Hex hex : activeHexes) {
            if (hex.isNeutral()) continue;
            if (hex.flag) continue;
            if (getProvinceByHex(hex) != null) continue;

            tempList.clear();
            propagationList.clear();
            propagationList.add(hex);
            hex.flag = true;
            propagateHex(tempList, propagationList);
            if (tempList.size() >= 2) {
                applyAdditionalProvince(tempList);
            }
        }

        for (Province province : provinces) {
            if (province.hasCapital()) continue;
            province.placeCapitalInRandomPlace(gameController.predictableRandom);
        }
    }


    private void applyAdditionalProvince(ArrayList<Hex> list) {
        Province intersectedProvince = getIntersectedProvince(list);
        if (intersectedProvince != null) {
            for (Hex hex : list) {
                if (intersectedProvince.containsHex(hex)) continue;
                intersectedProvince.addHex(hex);
            }
            return;
        }

        Province province = new Province(gameController, list);
        addProvince(province);
    }


    private Province getIntersectedProvince(ArrayList<Hex> list) {
        if (list.size() == 0) return null;
        int fraction = list.get(0).fraction;
        for (Hex hex : list) {
            for (Province province : provinces) {
                if (province.getFraction() != fraction) continue;
                if (!province.containsHex(hex)) continue;
                return province;
            }
        }
        return null;
    }


    private void propagateHex(ArrayList<Hex> tempList, ArrayList<Hex> propagationList) {
        Hex tempHex;
        Hex adjHex;
        while (propagationList.size() > 0) {
            tempHex = propagationList.get(0);
            tempList.add(tempHex);
            propagationList.remove(0);
            for (int dir = 0; dir < 6; dir++) {
                adjHex = tempHex.getAdjacentHex(dir);

                if (!adjHex.active) continue;
                if (!adjHex.sameFraction(tempHex)) continue;
                if (adjHex.flag) continue;

                propagationList.add(adjHex);
                adjHex.flag = true;
            }
        }
    }


    public void forceAnimEndInHex(Hex hex) {
        hex.animFactor.setValues(1, 0);
    }


    public int howManyPalms() {
        int c = 0;
        for (Hex activeHex : activeHexes) {
            if (activeHex.objectInside == Obj.PALM) c++;
        }
        return c;
    }


    public void expandTrees() {
        if (GameRules.replayMode) return;

        ArrayList<Hex> newPalmsList = getNewPalmsList();
        ArrayList<Hex> newPinesList = getNewPinesList();

        for (int i = newPalmsList.size() - 1; i >= 0; i--) {
            spawnPalm(newPalmsList.get(i));
        }

        for (int i = newPinesList.size() - 1; i >= 0; i--) {
            spawnPine(newPinesList.get(i));
        }

        for (Hex activeHex : activeHexes) {
            if (activeHex.containsTree() && activeHex.blockToTreeFromExpanding) {
                activeHex.blockToTreeFromExpanding = false;
            }
        }
    }


    private ArrayList<Hex> getNewPinesList() {
        ArrayList<Hex> newPinesList = new ArrayList<Hex>();

        for (Hex hex : activeHexes) {
            if (gameController.ruleset.canSpawnPineOnHex(hex)) {
                newPinesList.add(hex);
            }
        }

        return newPinesList;
    }


    private ArrayList<Hex> getNewPalmsList() {
        ArrayList<Hex> newPalmsList = new ArrayList<Hex>();

        for (Hex hex : activeHexes) {
            if (gameController.ruleset.canSpawnPalmOnHex(hex)) {
                newPalmsList.add(hex);
            }
        }

        return newPalmsList;
    }


    private void spawnPine(Hex hex) {
        if (!hex.canContainObjects) return;

        addSolidObject(hex, Obj.PINE);
        addAnimHex(hex);
        hex.animFactor.setValues(1, 0);
        gameController.replayManager.onPineSpawned(hex);
    }


    private void spawnPalm(Hex hex) {
        if (!hex.canContainObjects) return;

        addSolidObject(hex, Obj.PALM);
        addAnimHex(hex);
        hex.animFactor.setValues(1, 0);
        gameController.replayManager.onPalmSpawned(hex);
    }


    public void createPlayerHexCount() {
        playerHexCount = new int[GameRules.fractionsQuantity];
    }


    public void checkAnimHexes() {
        // important
        // this fucking anims hexes have to live long enough
        // if killed too fast, graphic bugs will show
        if (gameController.isSomethingMoving()) {
            timeToCheckAnimHexes = gameController.getCurrentTime() + 100;
            return;
        }
        letsCheckAnimHexes = false;
        ListIterator iterator = animHexes.listIterator();
        while (iterator.hasNext()) {
            Hex h = (Hex) iterator.next();
            if (h.animFactor.get() > 0.99 && !(h.containsUnit() && h.unit.moveFactor.get() < 1) && System.currentTimeMillis() > h.animStartTime + 250) {
                h.changingFraction = false;
                iterator.remove();
            }
        }
    }


    public boolean atLeastOneUnitIsReadyToMove() {
        for (Unit unit : gameController.getUnitList()) {
            if (unit.isReadyToMove()) return true;
        }
        return false;
    }


    public int getPredictionForWinner() {
        int numbers[] = new int[GameRules.fractionsQuantity];
        for (Hex activeHex : activeHexes) {
            if (activeHex.isNeutral()) continue;
            numbers[activeHex.fraction]++;
        }

        int max = numbers[0];
        int maxIndex = 0;
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] > max) {
                max = numbers[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }


    public boolean areConditionsGoodForPlayer() {
        int numbers[] = new int[GameRules.fractionsQuantity];
        for (Hex activeHex : activeHexes) {
            if (activeHex.isNeutral()) continue;
            numbers[activeHex.fraction]++;
        }

        int max = GameController.maxNumberFromArray(numbers);
        return max - numbers[0] < 2;
    }


    public void onEndCreation() {
        clearAnims();
        updateHexInsideLevelStatuses();
        defenseTips.clear();

        diplomacyManager.onEndCreation();
        fogOfWarManager.onEndCreation();
        updateInitialLevelString();
    }


    private void updateInitialLevelString() {
        initialLevelString = gameController.gameSaver.legacyExportManager.getFullLevelString();
    }


    public void onUserLevelLoaded() {
        updateInitialLevelString();
    }


    public void clearAnims() {
        ListIterator iterator = animHexes.listIterator();
        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }


    public void createFieldMatrix() {
        for (int i = 0; i < fWidth; i++) {
            field[i] = new Hex[fHeight];
            for (int j = 0; j < fHeight; j++) {
                field[i][j] = new Hex(i, j, fieldPos, this);
                field[i][j].ignoreTouch = false;
            }
        }
    }


    public void marchUnitsToHex(Hex target) {
        if (!gameController.selectionManager.isSomethingSelected()) return;
        if (!target.isSelected()) return;

        MassMarchManager massMarchManager = gameController.fieldManager.massMarchManager;
        massMarchManager.clearChosenUnits();
        if (selectedProvince.hasSomeoneReadyToMove()) {
            gameController.takeSnapshot();
            for (Hex hex : selectedProvince.hexList) {
                if (hex.containsUnit() && hex.unit.isReadyToMove()) {
                    massMarchManager.addChosenUnit(hex.unit);
                }
            }
            massMarchManager.performMarch(target);
        }

        setResponseAnimHex(target);
        SoundManagerYio.playSound(SoundManagerYio.soundHoldToMarch);
    }


    public void setResponseAnimHex(Hex hex) {
        responseAnimHex = hex;
        responseAnimFactor.setValues(1, 0.07);
        responseAnimFactor.destroy(1, 2);
    }


    public void selectAdjacentHexes(Hex startHex) {
        setSelectedProvince(startHex);
        if (selectedProvince == null) return;

        ListIterator listIterator = selectedHexes.listIterator();
        for (Hex hex : selectedProvince.hexList) {
            hex.select();
            if (!selectedHexes.contains(hex)) listIterator.add(hex);
        }
        showBuildOverlay();
    }


    public void showBuildOverlay() {
        if (SettingsManager.fastConstructionEnabled) {
            Scenes.sceneFastConstructionPanel.create();
        } else {
            Scenes.sceneSelectionOverlay.create();
        }
        Scenes.sceneFinances.create();
    }


    public void setSelectedProvince(Hex hex) {
        selectedProvince = getProvinceByHex(hex);
        if (selectedProvince == null) return;

        gameController.selectionManager.getSelMoneyFactor().setDy(0);
        gameController.selectionManager.getSelMoneyFactor().appear(3, 2);
    }


    public void updateHexPositions() {
        updateFieldPos();

        for (int i = 0; i < fWidth; i++) {
            for (int j = 0; j < fHeight; j++) {
                Hex hex = field[i][j];

                hex.updatePos();
                if (hex.containsUnit()) {
                    hex.unit.updateCurrentPos();
                }
            }
        }
    }


    public boolean isCityNameUsed(String string) {
        for (Province province : provinces) {
            if (province.name == null) continue;
            if (province.name.equals(string)) return true;
        }
        for (EditorProvinceData editorProvinceData : gameController.levelEditorManager.editorProvinceManager.provincesList) {
            if (editorProvinceData.name == null) continue;
            if (editorProvinceData.name.equals(string)) return true;
        }
        if (GameRules.diplomacyEnabled) {
            for (DiplomaticEntity entity : diplomacyManager.entities) {
                if (entity.capitalName == null) continue;
                if (entity.capitalName.equals(string)) return true;
            }
        }
        return gameController.namingManager.isNameUsed(string);
    }


    public Hex getHexByPos(double x, double y) {
        int j = (int) ((x - fieldPos.x) / (hexStep2 * sin60));
        int i = (int) ((y - fieldPos.y - hexStep2 * j * cos60) / hexStep1);
        if (i < 0 || i > fWidth - 1 || j < 0 || j > fHeight - 1) return null;

        Hex adjHex, resHex = field[i][j];
        x -= gameController.getYioGdxGame().gameView.hexViewSize;
        y -= gameController.getYioGdxGame().gameView.hexViewSize;

        double currentDistance, minDistance = Yio.distance(resHex.pos.x, resHex.pos.y, x, y);
        for (int k = 0; k < 6; k++) {
            adjHex = adjacentHex(field[i][j], k);
            if (adjHex == null || !adjHex.active) continue;
            currentDistance = Yio.distance(adjHex.pos.x, adjHex.pos.y, x, y);
            if (currentDistance < minDistance) {
                minDistance = currentDistance;
                resHex = adjHex;
            }
        }

        return resHex;
    }


    public Hex getHex(int i, int j) {
        if (i < 0 || i > fWidth - 1 || j < 0 || j > fHeight - 1) return null;

        return field[i][j];
    }


    public Hex adjacentHex(int i, int j, int direction) {
        switch (direction) {
            case 0:
                if (i >= fWidth - 1) return nullHex;
                return field[i + 1][j];
            case 1:
                if (j >= fHeight - 1) return nullHex;
                return field[i][j + 1];
            case 2:
                if (i <= 0 || j >= fHeight - 1) return nullHex;
                return field[i - 1][j + 1];
            case 3:
                if (i <= 0) return nullHex;
                return field[i - 1][j];
            case 4:
                if (j <= 0) return nullHex;
                return field[i][j - 1];
            case 5:
                if (i >= fWidth - 1 || j <= 0) return nullHex;
                return field[i + 1][j - 1];
            default:
                return nullHex;
        }
    }


    public void spawnTree(Hex hex) {
        if (!hex.active) return;
        if (hex.isNearWater()) addSolidObject(hex, Obj.PALM);
        else addSolidObject(hex, Obj.PINE);
    }


    public void addSolidObject(Hex hex, int type) {
        if (hex == null || !hex.active) return;
        if (hex.objectInside == type) return;
        if (!hex.canContainObjects) return;

        if (solidObjects.contains(hex)) {
            cleanOutHex(hex);
        }

        hex.setObjectInside(type);
        solidObjects.listIterator().add(hex);
    }


    public void removeUnitFromHex(Hex hex) {
        if (!hex.containsUnit()) return;

        gameController.getMatchStatistics().onUnitKilled();
        gameController.getUnitList().remove(hex.unit);
        hex.unit = null;
        addAnimHex(hex);
    }


    public void cleanOutHex(Hex hex) {
        removeUnitFromHex(hex);
        hex.setObjectInside(0);
        addAnimHex(hex);
        ListIterator iterator = solidObjects.listIterator();
        while (iterator.hasNext()) {
            if (iterator.next() == hex) {
                iterator.remove();
                return;
            }
        }
    }


    public void destroyBuildingsOnHex(Hex hex) {
        boolean hadHouse = (hex.objectInside == Obj.TOWN);
        if (hex.containsBuilding()) {
            cleanOutHex(hex);
        }
        if (hadHouse) {
            spawnTree(hex);
        }
    }


    public boolean buildUnit(Province province, Hex hex, int strength) {
        if (province == null || hex == null) return false;

        // units can only spawn on own territory
        if (!province.hexList.contains(hex)) return false;

        if (!hex.canHostBuiltUnit()) return false;

        if (!province.canBuildUnit(strength)) {
            tickleMoneySign();
            return false;
        }

        // check for unmergeable situation
        if (isUnmergeableSituationDetected(province, hex, strength)) return false;

        gameController.takeSnapshot();
        province.money -= GameRules.PRICE_UNIT * strength;
        gameController.getMatchStatistics().onMoneySpent(gameController.turn, GameRules.PRICE_UNIT * strength);
        gameController.replayManager.onUnitBuilt(province, hex, strength);

        buildUnitPeacefully(hex, strength);
        return true;
    }


    private void buildUnitPeacefully(Hex hex, int strength) {
        if (!hex.containsUnit()) {
            placeUnitPreservingHostBuilding(hex, strength);
            return;
        }

        // merge units
        Unit newUnit = new Unit(gameController, hex, strength);
        newUnit.setReadyToMove(true);
        gameController.matchStatistics.unitsDied++;
        gameController.mergeUnits(hex, newUnit, hex.unit);
    }


    private boolean isUnmergeableSituationDetected(Province province, Hex hex, int strength) {
        return hex.sameFraction(province) && hex.containsUnit() && !gameController.canMergeUnits(strength, hex.unit.strength);
    }


    private void tickleMoneySign() {
        if (!gameController.isPlayerTurn()) return;
        gameController.tickleMoneySign();
    }


    public boolean buildTower(Province province, Hex hex) {
        if (province == null) return false;
        if (province.hasMoneyForTower()) {
            gameController.takeSnapshot();
            gameController.replayManager.onTowerBuilt(hex, false);
            addSolidObject(hex, Obj.TOWER);
            addAnimHex(hex);
            province.money -= GameRules.PRICE_TOWER;
            gameController.getMatchStatistics().onMoneySpent(gameController.turn, GameRules.PRICE_TOWER);
            gameController.updateCacheOnceAfterSomeTime();
            return true;
        }

        // can't build tower
        tickleMoneySign();
        return false;
    }


    public boolean buildStrongTower(Province province, Hex hex) {
        if (province == null) return false;

        if (province.hasMoneyForStrongTower()) {
            gameController.takeSnapshot();
            gameController.replayManager.onTowerBuilt(hex, true);
            addSolidObject(hex, Obj.STRONG_TOWER);
            addAnimHex(hex);
            province.money -= GameRules.PRICE_STRONG_TOWER;
            gameController.getMatchStatistics().onMoneySpent(gameController.turn, GameRules.PRICE_STRONG_TOWER);
            gameController.updateCacheOnceAfterSomeTime();
            return true;
        }

        // can't build tower
        tickleMoneySign();
        return false;
    }


    public boolean buildFarm(Province province, Hex hex) {
        if (province == null) return false;

        if (!MoveZoneDetection.canBuildFarmOnHex(hex)) {
            return false;
        }

        if (province.hasMoneyForFarm()) {
            gameController.takeSnapshot();
            gameController.replayManager.onFarmBuilt(hex);
            province.money -= province.getCurrentFarmPrice();
            gameController.getMatchStatistics().onMoneySpent(gameController.turn, province.getCurrentFarmPrice());
            addSolidObject(hex, Obj.FARM);
            addAnimHex(hex);
            gameController.updateCacheOnceAfterSomeTime();
            return true;
        }

        // can't build farm
        tickleMoneySign();
        return false;
    }


    public boolean buildPort(Province province, Hex hex) {
        if (province == null) return false;

        if (!MoveZoneDetection.canBuildPortOnHex(province, hex)) {
            return false;
        }

        if (province.hasMoneyForPort()) {
            int price = province.getCurrentPortPrice();
            gameController.takeSnapshot();
            gameController.replayManager.onPortBuilt(hex, province.getFraction());
            province.money -= price;
            gameController.getMatchStatistics().onMoneySpent(gameController.turn, price);
            activatePortHex(province, hex);
            addSolidObject(hex, Obj.PORT);
            addAnimHex(hex);
            gameController.updateCacheOnceAfterSomeTime();
            return true;
        }

        // can't build port
        tickleMoneySign();
        return false;
    }


    /**
     * Takes a razed port back to open water - the exact inverse of {@link #activatePortHex}. A port
     * is the one building that stands on the sea, so destroying it must return the tile to the sea.
     * Letting the attacker capture it instead, the way any other hex is captured, quietly
     * manufactured a new land tile out in the water every time a harbour changed hands.
     */
    public void sinkPortHex(Hex hex) {
        int previousFraction = hex.fraction;
        int previousObject = hex.objectInside;

        cleanOutHex(hex); // the port itself, its garrison, and the solid-object entry
        hex.active = false;
        hex.overseasPart = false;
        hex.setFraction(GameRules.NEUTRAL_FRACTION);
        hex.previousFraction = GameRules.NEUTRAL_FRACTION;
        activeHexes.remove(hex);
        addAnimHex(hex);

        // the province just lost a tile, and a harbour can be the only thing joining two stretches
        // of its coast - so the province may genuinely split in two here
        splitProvince(hex, previousFraction, previousObject);
        gameController.updateCacheOnceAfterSomeTime();
    }


    /**
     * A port claims the water tile it stands on: the hex becomes an active part of the province, so
     * income, saving, undo and conquest all treat it as ordinary territory from here on. Must run
     * before addSolidObject, which refuses inactive hexes.
     */
    private void activatePortHex(Province province, Hex hex) {
        hex.active = true;
        hex.setFraction(province.getFraction());
        hex.previousFraction = province.getFraction();
        activeHexes.add(hex);
        province.addHex(hex);

        if (selectedProvince == province) {
            selectAdjacentHexes(hex);
        }
    }


    public boolean buildTree(Province province, Hex hex) {
        if (province == null) return false;
        if (province.hasMoneyForTree()) {
            gameController.takeSnapshot();
            spawnTree(hex);
            addAnimHex(hex);
            province.money -= GameRules.PRICE_TREE;
            gameController.getMatchStatistics().onMoneySpent(gameController.turn, GameRules.PRICE_TREE);
            gameController.updateCacheOnceAfterSomeTime();
            return true;
        }

        // can't build tree
        tickleMoneySign();
        return false;
    }


    /**
     * Places a unit without crushing whatever stands on the hex. Used when restoring a saved
     * game: a unit docked at a port coexists with the port, so the usual crush path of
     * addUnit() would wrongly destroy the building.
     */
    public Unit addUnitWithoutCrushingObject(Hex hex, int strength) {
        if (hex == null) return null;
        hex.addUnit(strength);
        return hex.unit;
    }


    /**
     * Places a unit on a hex a bought unit is allowed to occupy. Ordinary land crushes whatever
     * stands there, while the capital and ports keep their building. A unit that ends up on a
     * port is docked there, exactly as if it had walked in.
     */
    public Unit placeUnitPreservingHostBuilding(Hex hex, int strength) {
        if (!hex.containsBuilding()) return addUnit(hex, strength);

        Unit unit = addUnitWithoutCrushingObject(hex, strength);
        if (hex.objectInside == Obj.PORT) {
            unit.ship = true;
            unit.originHex = hex;
        }
        checkToPrepareNewlyAddedUnitForMovement(unit);
        return unit;
    }


    /**
     * Takes a unit off a hex without harming a building it was standing on.
     */
    public void removeUnitPreservingHostBuilding(Hex hex) {
        if (hex.containsBuilding()) {
            removeUnitFromHex(hex);
            return;
        }
        cleanOutHex(hex);
    }


    public Unit addUnit(Hex hex, int strength) {
        if (hex == null) return null;
        if (hex.containsObject()) {
            gameController.ruleset.onUnitAdd(hex);
            cleanOutHex(hex);
            gameController.updateCacheOnceAfterSomeTime();
            hex.addUnit(strength);
        } else {
            hex.addUnit(strength);
            checkToPrepareNewlyAddedUnitForMovement(hex.unit);
        }
        return hex.unit;
    }


    private void checkToPrepareNewlyAddedUnitForMovement(Unit unit) {
        if (!gameController.isUnitValidForMovement(unit)) return;
        unit.setReadyToMove(true);
        unit.startJumping();
    }


    public void addProvince(Province province) {
        if (provinces.contains(province)) return;
        if (containsEqualProvince(province)) {
            System.out.println("Problem in FieldController.addProvince()");
            Yio.printStackTrace();
            return;
        }

        provinces.add(province);
    }


    public boolean containsEqualProvince(Province province) {
        for (Province p : provinces) {
            if (p.equals(province)) {
                return true;
            }
        }

        return false;
    }


    public Hex adjacentHex(Hex hex, int direction) {
        return adjacentHex(hex.index1, hex.index2, direction);
    }


    public boolean hexHasSelectedNearby(Hex hex) {
        for (int i = 0; i < 6; i++)
            if (hex.getAdjacentHex(i).selected) return true;
        return false;
    }


    public static float distanceBetweenHexes(Hex one, Hex two) {
        PointYio pOne = one.getPos();
        PointYio pTwo = two.getPos();
        return (float) pOne.distanceTo(pTwo);
    }


    public boolean isSomethingSelected() {
        return selectedHexes.size() > 0;
    }


    public void giveMoneyToPlayerProvinces(int amount) {
        for (Province province : provinces) {
            if (province.getFraction() == 0) {
                province.money += amount;
            }
        }
    }


    public boolean hexHasNeighbourWithFraction(Hex hex, int fraction) {
        Hex neighbour;
        for (int i = 0; i < 6; i++) {
            neighbour = hex.getAdjacentHex(i);
            if (neighbour != null && neighbour.active && neighbour.sameFraction(fraction)) return true;
        }
        return false;
    }


    public void addAnimHex(Hex hex) {
        if (animHexes.contains(hex)) return;
        if (DebugFlags.testMode) return;

        animHexes.listIterator().add(hex);

        hex.animFactor.setValues(0, 0);
        hex.animFactor.appear(1, 1);
        hex.animStartTime = System.currentTimeMillis();

        gameController.updateCacheOnceAfterSomeTime();
    }


    public Province findProvinceCopy(Province src) {
        Province result;
        for (Hex hex : src.hexList) {
            result = getProvinceByHex(hex);
            if (result == null) continue;
            return result;
        }
        return null;
    }


    public Province findProvince(int fraction) {
        for (Province province : provinces) {
            if (province.getFraction() != fraction) continue;
            return province;
        }

        return null;
    }


    public Province getRandomProvince() {
        int index = YioGdxGame.random.nextInt(provinces.size());
        return provinces.get(index);
    }


    public void checkToFocusCameraOnCurrentPlayer() {
        if (!gameController.isInMultiplayerMode()) return;
        if (!gameController.isPlayerTurn()) return;

        Province province = findProvince(gameController.turn);
        if (province == null) return;

        province.focusCameraOnThis();
    }


    public Province getBiggestProvince(int fraction) {
        Province bestProvince = null;
        for (Province province : provinces) {
            if (province.getFraction() != fraction) continue;
            if (bestProvince == null || province.hexList.size() > bestProvince.hexList.size()) {
                bestProvince = province;
            }
        }
        return bestProvince;
    }


    public boolean isOnlyOneFractionAlive(int fraction) {
        boolean detected = false;
        for (Province province : provinces) {
            if (province.getFraction() != fraction) return false;
            detected = true;
        }
        return detected;
    }


    public Province getProvinceByHex(Hex hex) {
        for (Province province : provinces) {
            if (!province.containsHex(hex)) continue;
            return province;
        }

        return null;
    }


    public Hex getRandomActiveHex() {
        int index = YioGdxGame.random.nextInt(activeHexes.size());
        return activeHexes.get(index);
    }


    public Province getMaxProvinceFromList(ArrayList<Province> list) {
        if (list.size() == 0) return null;
        Province max, temp;
        max = list.get(0);
        for (int k = list.size() - 1; k >= 0; k--) {
            temp = list.get(k);
            if (temp.hexList.size() > max.hexList.size()) max = temp;
        }
        return max;
    }


    public void splitProvince(Hex hex, int fraction, int previousObject) {
        Province oldProvince = getProvinceByHex(hex);
        if (oldProvince == null) return;
        MoveZoneDetection.unFlagAllHexesInArrayList(oldProvince.hexList);
        tempList.clear();
        propagationList.clear();
        ArrayList<Province> provincesAdded = new ArrayList<Province>();
        // overseas beachheads have no land connection to the mainland, so the flood fill can
        // never reach them from the captured hex's neighborhood; they are collected separately
        // and re-attached to whatever mainland survives the split
        ArrayList<ArrayList<Hex>> overseasComponents = new ArrayList<>();
        Hex startHex, tempHex, adjHex;
        hex.flag = true;
        gameController.getPredictableRandom().setSeed(hex.index1 + hex.index2);
        // seeding from the whole hex list (instead of just the captured hex's neighbors) is
        // equivalent for a connected province, and additionally finds disconnected overseas parts
        for (Hex seed : oldProvince.hexList) {
            startHex = seed;
            if (!startHex.active || startHex.fraction != fraction || startHex.flag) continue;
            tempList.clear();
            propagationList.clear();
            propagationList.add(startHex);
            startHex.flag = true;
            while (propagationList.size() > 0) {
                tempHex = propagationList.get(0);
                tempList.add(tempHex);
                propagationList.remove(0);
                for (int i = 0; i < 6; i++) {
                    adjHex = tempHex.getAdjacentHex(i);
                    if (adjHex.active && adjHex.sameFraction(tempHex) && !adjHex.flag) {
                        propagationList.add(adjHex);
                        adjHex.flag = true;
                    }
                }
            }
            if (isOverseasComponent(tempList)) {
                overseasComponents.add(new ArrayList<>(tempList));
            } else if (tempList.size() >= 2) {
                Province province = new Province(gameController, tempList);
                province.money = 0;
                if (!province.hasCapital()) {
                    province.placeCapitalInRandomPlace(gameController.getPredictableRandom());
                    gameController.namingManager.checkForCapitalRelocate(previousObject, hex, province);
                }
                addProvince(province);
                provincesAdded.add(province);
            } else {
                destroyBuildingsOnHex(startHex);
            }
        }
        if (provincesAdded.size() > 0 && !(hex.objectInside == Obj.TOWN)) {
            getMaxProvinceFromList(provincesAdded).money = oldProvince.money;
        }
        Province mainland = getMaxProvinceFromList(provincesAdded);
        for (ArrayList<Hex> component : overseasComponents) {
            if (mainland != null) {
                for (Hex overseasHex : component) {
                    mainland.addHex(overseasHex);
                }
            } else {
                makeColonyIndependent(component);
            }
        }
        removeProvince(oldProvince);
        diplomacyManager.updateEntityAliveStatus(fraction);
    }


    private boolean isOverseasComponent(ArrayList<Hex> component) {
        for (Hex hex : component) {
            if (!hex.overseasPart) return false;
        }
        return true;
    }


    public void checkToUniteProvinces(Hex hex) {
        ArrayList<Province> adjacentProvinces = new ArrayList<Province>();
        Province p;
        for (int i = 0; i < 6; i++) {
            Hex adjHex = hex.getAdjacentHex(i);
            // touching a colony must not merge its distant motherland with a local province;
            // instead the colony alone gets absorbed later, in checkToAbsorbOverseasParts()
            if (adjHex.overseasPart) continue;
            p = getProvinceByHex(adjHex);
            if (p != null && hex.sameFraction(p) && !adjacentProvinces.contains(p)) adjacentProvinces.add(p);
        }
        if (adjacentProvinces.size() >= 2) {
            int sum = 0;
            Hex capital = getMaxProvinceFromList(adjacentProvinces).getCapital();
            ArrayList<Hex> hexArrayList = new ArrayList<Hex>();
//            YioGdxGame.say("uniting provinces: " + adjacentProvinces.size());
            for (Province province : adjacentProvinces) {
                sum += province.money;
                hexArrayList.addAll(province.hexList);
                removeProvince(province);
            }
            Province unitedProvince = new Province(gameController, hexArrayList);
            unitedProvince.money = sum;
            unitedProvince.setCapital(capital);
            addProvince(unitedProvince);
        }
    }


    private void removeProvince(Province province) {
        provinces.remove(province);
    }


    public void joinHexToAdjacentProvince(Hex hex) {
        Province p;
        for (int i = 0; i < 6; i++) {
            Hex adjacentHex = hex.getAdjacentHex(i);
            p = getProvinceByHex(adjacentHex);
            if (p != null && hex.sameFraction(p)) {
                p.addHex(hex);
                // growing out of a beachhead: the new hex is as overseas as the hex it grew from
                if (adjacentHex.overseasPart) {
                    hex.overseasPart = true;
                }
                Hex h;
                for (int j = 0; j < 6; j++) {
                    h = adjacentHex(hex, j);
                    if (h.active && h.sameFraction(hex) && getProvinceByHex(h) == null) p.addHex(h);
                }
                return;
            }
        }
    }


    /**
     * A colony stops being a colony the moment it gains a land border with friendly mainland.
     * If that mainland is another province, the colony's hexes are handed over to it (only the
     * overseas part moves - the motherland keeps its own territory and treasury).
     */
    public void checkToAbsorbOverseasParts(int fraction) {
        boolean repeat = true;
        while (repeat) {
            repeat = false;
            for (Province province : provinces) {
                if (province.getFraction() != fraction) continue;
                for (Hex hex : province.hexList) {
                    if (!hex.overseasPart) continue;
                    Province mainland = getAdjacentMainlandProvince(hex);
                    if (mainland == null) continue;
                    absorbOverseasComponent(hex, province, mainland);
                    repeat = true; // hex lists changed, restart iteration
                    break;
                }
                if (repeat) break;
            }
        }
    }


    private Province getAdjacentMainlandProvince(Hex hex) {
        for (int dir = 0; dir < 6; dir++) {
            Hex adjHex = hex.getAdjacentHex(dir);
            if (!adjHex.active) continue;
            if (adjHex.overseasPart) continue;
            if (!adjHex.sameFraction(hex)) continue;
            Province province = getProvinceByHex(adjHex);
            if (province != null) return province;
        }
        return null;
    }


    private void absorbOverseasComponent(Hex startHex, Province owner, Province receiver) {
        ArrayList<Hex> component = new ArrayList<>();
        ArrayList<Hex> propagation = new ArrayList<>();
        component.add(startHex);
        propagation.add(startHex);
        while (propagation.size() > 0) {
            Hex tempHex = propagation.remove(0);
            for (int i = 0; i < 6; i++) {
                Hex adjHex = tempHex.getAdjacentHex(i);
                if (!adjHex.active) continue;
                if (!adjHex.overseasPart) continue;
                if (!adjHex.sameFraction(startHex)) continue;
                if (component.contains(adjHex)) continue;
                component.add(adjHex);
                propagation.add(adjHex);
            }
        }

        for (Hex hex : component) {
            hex.overseasPart = false;
            if (owner == receiver) continue;
            owner.hexList.remove(hex);
            receiver.addHex(hex);
        }
    }


    /**
     * Finds a province of the given fraction that owns a hex adjacent to the given one.
     * Used by amphibious landings, where the attacker arrives from the sea instead of
     * from a province of its own.
     */
    public Province getAdjacentProvince(Hex hex, int fraction) {
        for (int dir = 0; dir < 6; dir++) {
            Hex adjHex = hex.getAdjacentHex(dir);
            if (!adjHex.active) continue;
            if (adjHex.fraction != fraction) continue;
            Province province = getProvinceByHex(adjHex);
            if (province != null) return province;
        }
        return null;
    }


    public void updatePointByHexIndexes(PointYio pointYio, int index1, int index2) {
        pointYio.x = fieldPos.x + hexStep2 * index2 * sin60;
        pointYio.y = fieldPos.y + hexStep1 * index1 + hexStep2 * index2 * cos60;
    }


    public void setHexFraction(Hex hex, int fraction) {
        int previousObject = hex.objectInside;
        cleanOutHex(hex);
        int previousFraction = hex.fraction;
        hex.overseasPart = false; // the conqueror decides anew whether this hex is a beachhead
        hex.setFraction(fraction);
        splitProvince(hex, previousFraction, previousObject);
        checkToUniteProvinces(hex);
        joinHexToAdjacentProvince(hex);
        checkToAbsorbOverseasParts(fraction);
        ListIterator animIterator = animHexes.listIterator();

        for (int dir = 0; dir < 6; dir++) {
            Hex adj = hex.getAdjacentHex(dir);
            if (adj != null && adj.active && adj.sameFraction(hex)) {
                if (!animHexes.contains(adj)) {
                    animIterator.add(adj);
                }
                if (!adj.changingFraction) {
                    adj.animFactor.setValues(1, 0);
                }
            }
        }
        hex.changingFraction = true;
        if (!animHexes.contains(hex)) animIterator.add(hex);
        hex.animFactor.setValues(0, 0);
        hex.animFactor.appear(1, 1);

        if (!gameController.isPlayerTurn()) {
            forceAnimEndInHex(hex);
        }
    }


    public void updateFocusedHex() {
        updateFocusedHex(gameController.touchPoint.x, gameController.touchPoint.y);
    }


    public void updateFocusedHex(float screenX, float screenY) {
        OrthographicCamera orthoCam = gameController.cameraController.orthoCam;
        SelectionManager selectionManager = gameController.selectionManager;

        selectionManager.selectX = (screenX - 0.5f * GraphicsYio.width) * orthoCam.zoom + orthoCam.position.x;
        selectionManager.selectY = (screenY - 0.5f * GraphicsYio.height) * orthoCam.zoom + orthoCam.position.y;
        gameController.convertedTouchPoint.set(selectionManager.selectX, selectionManager.selectY);

        GameView gameView = gameController.getYioGdxGame().gameView;
        float x = selectionManager.selectX + gameView.hexViewSize;
        float y = selectionManager.selectY + gameView.hexViewSize;

        focusedHex = getHexByPos(x, y);
    }


    public boolean isAtLeastOneCurrentFractionProvinceAlive() {
        for (Province province : provinces) {
            if (province.getFraction() != gameController.turn) continue;
            if (province.hexList.size() == 0) continue;
            return true;
        }
        return false;
    }


    @Override
    public String encode() {
        StringBuilder builder = new StringBuilder();
        for (Hex activeHex : activeHexes) {
            builder.append(activeHex.encode()).append(",");
        }
        return builder.toString();
    }


    @Override
    public void decode(String source) {
        for (String token : source.split(",")) {
            String[] split = token.split(" ");
            int index1 = Integer.valueOf(split[0]);
            int index2 = Integer.valueOf(split[1]);
            Hex hex = field[index1][index2];
            hex.active = true;
            setHexFraction(hex, Integer.valueOf(split[2]));
            hex.decode(token);
            activeHexes.add(0, hex);
        }
    }
}