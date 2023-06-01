package hardwar.branch.prediction.judged.PAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SIPO2", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        Bit[] address = this.getCacheEntry(branchInstruction.getInstructionAddress(), this.PABHR.read(branchInstruction.getInstructionAddress()).read());
        PAPHT.putIfAbsent(address, getDefaultBlock());
        SC.load(PAPHT.get(address));
        return BranchResult.of(SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        // TODO:complete Task 2

        Bit[] address = this.getCacheEntry(instruction.getInstructionAddress(), this.PABHR.read(instruction.getInstructionAddress()).read());

        Bit[] temp = SC.read();
        if (actual == BranchResult.TAKEN) {
            temp = CombinationalLogic.count(temp, true, CountMode.SATURATING);
        } else if (actual == BranchResult.NOT_TAKEN) {
            temp = CombinationalLogic.count(temp, false, CountMode.SATURATING);
        }
        PAPHT.put(address, temp);

        ShiftRegister arr = this.PABHR.read(instruction.getInstructionAddress());
        if (actual == BranchResult.TAKEN) {
            arr.insert(Bit.ONE);
            this.PABHR.write(instruction.getInstructionAddress() , arr.read());
        } else if (actual == BranchResult.NOT_TAKEN) {
            arr.insert(Bit.ZERO);
            this.PABHR.write(instruction.getInstructionAddress() , arr.read());
        }
    }


    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
