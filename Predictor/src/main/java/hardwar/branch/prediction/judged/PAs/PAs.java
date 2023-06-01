package hardwar.branch.prediction.judged.PAs;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final HashMode hashMode;
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address Branch History Register
    private final Cache<Bit[], Bit[]> PSPHT; // Per Set Predication History Table

    public PAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public PAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = HashMode.XOR;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with K bit as PHT selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(KSize, 1 << BHRSize, SCSize);

        // Initialize the saturating counter
        SC = new SIPORegister("SIPO2", SCSize, null);
    }

    /**
     * predicts the result of a branch instruction based on the per address BHR and hash value of branch
     * instruction address
     *
     * @param branchInstruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        Bit[] address = branchInstruction.getInstructionAddress();
        address = CombinationalLogic.hash(address, this.KSize, this.hashMode);
        address = this.getCacheEntry(address, this.PABHR.read(branchInstruction.getInstructionAddress()).read());
        PSPHT.putIfAbsent(address, getDefaultBlock());
        SC.load(PSPHT.get(address));
        return BranchResult.of(SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        Bit[] address = instruction.getInstructionAddress();
        address = CombinationalLogic.hash(address, this.KSize, this.hashMode);
        address = this.getCacheEntry(address, this.PABHR.read(instruction.getInstructionAddress()).read());

        Bit[] temp = SC.read();
        if (actual == BranchResult.TAKEN) {
            temp = CombinationalLogic.count(temp, true, CountMode.SATURATING);
        } else if (actual == BranchResult.NOT_TAKEN) {
            temp = CombinationalLogic.count(temp, false, CountMode.SATURATING);
        }
        PSPHT.put(address, temp);

        ShiftRegister arr = this.PABHR.read(instruction.getInstructionAddress());
        if (actual == BranchResult.TAKEN) {
            arr.insert(Bit.ONE);
            this.PABHR.write(instruction.getInstructionAddress() , arr.read());
        } else if (actual == BranchResult.NOT_TAKEN) {
            arr.insert(Bit.ZERO);
            this.PABHR.write(instruction.getInstructionAddress() , arr.read());
        }
    }

    @Override
    public String monitor() {
        return "PAs predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PSPHT.monitor();
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // hash the branch address
        Bit[] hashKSize = CombinationalLogic.hash(branchAddress, KSize, hashMode);

        // Concatenate the Hash bits with the BHR bits
        Bit[] cacheEntry = new Bit[hashKSize.length + BHRValue.length];
        System.arraycopy(hashKSize, 0, cacheEntry, 0, hashKSize.length);
        System.arraycopy(BHRValue, 0, cacheEntry, hashKSize.length, BHRValue.length);

        return cacheEntry;
    }


    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }
}
