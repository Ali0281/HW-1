package hardwar.branch.prediction.judged.SAs;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PSPHT; // per set predication history table
    private final HashMode hashMode;

    public SAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public SAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = HashMode.XOR;

        // Initialize the PSBHR with the given bhr and branch instruction size
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(KSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SIPO2", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        Bit[] address = branchInstruction.getInstructionAddress();
        address = CombinationalLogic.hash(address, this.KSize, this.hashMode);
        address = this.getCacheEntry(address, this.PSBHR.read(branchInstruction.getInstructionAddress()).read());
        PSPHT.putIfAbsent(address, getDefaultBlock());
        SC.load(PSPHT.get(address));
        return BranchResult.of(SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        Bit[] temp = SC.read();
        Bit[] address = CombinationalLogic.hash(branchInstruction.getInstructionAddress(), this.KSize, this.hashMode);
        if (actual == BranchResult.TAKEN) {
            temp = CombinationalLogic.count(temp, true, CountMode.SATURATING);
        } else if (actual == BranchResult.NOT_TAKEN) {
            temp = CombinationalLogic.count(temp, false, CountMode.SATURATING);
        }
        this.PSPHT.put(this.PSBHR.read(address).read(), temp);
        ShiftRegister arr = this.PSBHR.read(address);
        if (actual == BranchResult.TAKEN) {
            arr.insert(Bit.ONE);
            this.PSBHR.write(address, arr.read());
        } else if (actual == BranchResult.NOT_TAKEN) {
            arr.insert(Bit.ZERO);
            this.PSBHR.write(address , arr.read());
        }
    }


    private Bit[] getAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return CombinationalLogic.hash(branchAddress, KSize, hashMode);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, KSize);
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
        return null;
    }
}
