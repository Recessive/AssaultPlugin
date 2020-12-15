package main;

public class StringHandler {
    public static String determineRank(int xp){
        switch(xp / 20000){
            case 0: return "[accent]<[white]\uF7F61[accent]>";
            case 1: return "[accent]<[white]\uF7F62[accent]>";
            case 2: return "[accent]<[white]\uF7F63[accent]>";
            case 3: return "[accent]<[white]\uF7F64[accent]>";
            case 4: return "[accent]<[white]\uF7F65[accent]>";

            case 5: return "[accent]<[white]\uF7F51[accent]>";
            case 6: return "[accent]<[white]\uF7F52[accent]>";
            case 7: return "[accent]<[white]\uF7F53[accent]>";
            case 8: return "[accent]<[white]\uF7F54[accent]>";
            case 9: return "[accent]<[white]\uF7F55[accent]>";

            case 10: return "[accent]<[white]\uF7F41[accent]>";
            case 11: return "[accent]<[white]\uF7F42[accent]>";
            case 12: return "[accent]<[white]\uF7F43[accent]>";
            case 13: return "[accent]<[white]\uF7F44[accent]>";
            case 14: return "[accent]<[white]\uF7F45[accent]>";

            case 15: return "[accent]<[white]\uF7F31[accent]>";
            case 16: return "[accent]<[white]\uF7F32[accent]>";
            case 17: return "[accent]<[white]\uF7F33[accent]>";
            case 18: return "[accent]<[white]\uF7F34[accent]>";
            case 19: return "[accent]<[white]\uF7F35[accent]>";

            case 20: return "[accent]<[white]\uF7F21[accent]>";
            case 21: return "[accent]<[white]\uF7F22[accent]>";
            case 22: return "[accent]<[white]\uF7F23[accent]>";
            case 23: return "[accent]<[white]\uF7F24[accent]>";
            case 24: return "[accent]<[white]\uF7F25[accent]>";
        }
        return "[accent]<[#fa00dd]THICC[accent]>[white]";
    }
}
