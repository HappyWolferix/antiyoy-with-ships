package yio.tro.antiyoy.ai;

import yio.tro.antiyoy.gameplay.GameController;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;

public abstract class ArtificialIntelligenceGeneric extends ArtificialIntelligence{


    public static final int MAX_EXTRA_FARM_COST = 80;


    ArtificialIntelligenceGeneric(GameController gameController, int fraction) {
        super(gameController, fraction);
    }


    @Override
    protected void spendMoney(Province province) {
        tryToBuildTowers(province);
        tryToBuildFarms(province);
        tryToBuildUnits(province);
    }


    protected void tryToBuildFarms(Province province) {
//        if (province.getExtraFarmCost() > province.getIncome()) return;
        if (province.getExtraFarmCost() > MAX_EXTRA_FARM_COST) return;

        while (province.hasMoneyForFarm()) {
            if (buildingWouldSealProvince(province)) return;
            if (!isOkToBuildNewFarm(province)) return;
            Hex hex = findGoodHexForFarm(province);
            if (hex == null) return;
            gameController.fieldManager.buildFarm(province, hex);
        }
    }


    protected boolean isOkToBuildNewFarm(Province srcProvince) {
        if (srcProvince.money > 2 * srcProvince.getCurrentFarmPrice() && !buildingWouldSealProvince(srcProvince)) return true;

        if (findHexThatNeedsTower(srcProvince) != null) return false;

        return true;
    }


    protected int getArmyStrength(Province province) {
        int sum = 0;
        for (Hex hex : province.hexList) {
            if (hex.containsUnit()) {
                sum += hex.unit.strength;
            }
        }
        return sum;
    }


    /**
     * Farms go into the interior, not onto the frontier: perimeter hexes are the staging spots units
     * are bought on, and paving them cripples the province's ability to attack and defend.
     */
    protected Hex findGoodHexForFarm(Province province) {
        Hex result = null;
        int bestScore = Integer.MIN_VALUE;

        for (Hex hex : province.hexList) {
            if (!isHexGoodForFarm(hex)) continue;

            int score = numberOfFriendlyHexesNearby(hex);
            if (hex.isInPerimeter()) score -= 5;

            if (result == null || score > bestScore) {
                bestScore = score;
                result = hex;
            }
        }

        return result;
    }


    protected boolean hasProvinceGoodHexForFarm(Province province) {
        for (Hex hex : province.hexList) {
            if (!isHexGoodForFarm(hex)) continue;
            return true;
        }
        return false;
    }


    protected boolean isHexGoodForFarm(Hex hex) {
        if (!hex.isFree()) return false;
        if (!hex.hasThisSupportiveObjectNearby(Obj.TOWN) && !hex.hasThisSupportiveObjectNearby(Obj.FARM)) return false;
        return true;
    }
}
