package de.ohnes.AlgorithmicComponents.Shelves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.ohnes.AlgorithmicComponents.LongestProcessingTime;
import de.ohnes.AlgorithmicComponents.Knapsack.MDKnapsack;
import de.ohnes.util.Job;
import de.ohnes.util.MDKnapsackChoice;
import de.ohnes.util.MDKnapsackItem;
import de.ohnes.util.Machine;
import de.ohnes.util.MyMath;
import de.ohnes.util.Vector3D;

/**
 * An implementation of the Algorithm by Mounie, Rapine, Trystram
 */
public class CpuGpuApproach extends GrageApproach {

    public CpuGpuApproach() {
        super();
    }

    /**
     * finds a two two shedule for the instance I with deadline d, if a schedule of length d exists.
     * @param d the deadline (makespan guess)
     * @param epsilon the "the small error"
     * @return true if a schedule of length d exists, false if none exists.
     */
    @Override
    public boolean solve(double d, double epsilon) {
        //"forget about small jobs"

        // inverted delta
        final int invDelta = 6;
        final int n = I.getJobs().length;
        final double mu = (1.0 * n * invDelta) / d;

        List<Job> shelf2 = new ArrayList<>(Arrays.asList(MyMath.findBigJobs(I, d)));
        List<Job> smallJobs = new ArrayList<>(Arrays.asList(MyMath.findSmallJobs(I, d)));

        //transform to knapsack problem

        List<MDKnapsackItem> knapsackItems = new ArrayList<>();
        for (Job job : smallJobs) {
            MDKnapsackItem knapsackItem = new MDKnapsackItem();
            knapsackItem.setJob(job);
            //c_{i, S}
            knapsackItem.addChoice(MDKnapsackChoice.SMALL, job.getProcessingTime(1), new Vector3D(0, 0, 0));
            //c_{i, 3}
            //if a choice would certainly violate the deadline d, we do not allow it.
            if (job.getSequentialProcessingTime() <= d) {
                knapsackItem.addChoice(MDKnapsackChoice.SEQUENTIAL, 0, new Vector3D(0, job.getSequentialWeight(d), job.getScaledRoundedSequentialProcessingTime(mu)));
            }
            knapsackItems.add(knapsackItem);
        }
        for (Job job : shelf2) {
            MDKnapsackItem knapsackItem = new MDKnapsackItem();
            knapsackItem.setJob(job);
            //c_{i, 1}
            int dAllotment = job.canonicalNumberMachines(d);
            //if a choice would certainly violate the deadline d, we do not allow it.
            if (dAllotment > 0) {
                knapsackItem.addChoice(MDKnapsackChoice.SHELF1, job.getProcessingTime(dAllotment) * dAllotment, new Vector3D(dAllotment, 0, 0));
            }
            //c_{i, 2}
            int dHalfAllotment = job.canonicalNumberMachines(d/2);
            //if a choice would certainly violate the deadline d, we do not alow it.
            //here, if there is no dHalfAllotment, then the canonical number of processors is set to be infinite,
            //thus the cost would be infinite, thus the resulting work would not be below the threshold.
            if (dHalfAllotment > 0) {
                knapsackItem.addChoice(MDKnapsackChoice.SHELF2, job.getProcessingTime(dHalfAllotment) * dHalfAllotment, new Vector3D(0, 0, 0));
            }
            //c_{i, 3}
            //if a choice would certainly violate the deadline d, we do not allow it.
            if (job.getSequentialProcessingTime() <= d) {
                knapsackItem.addChoice(MDKnapsackChoice.SEQUENTIAL, 0, new Vector3D(0, job.getSequentialWeight(d), job.getScaledRoundedSequentialProcessingTime(mu)));
            }

            // if there is no valid choice for some job, then we must reject the deadline d.
            // this happens if a job can not be executed in time d on a sequential machine nor on m malleable machines.
            if (knapsackItem.getChoices().isEmpty()) {
                return false;
            }
            knapsackItems.add(knapsackItem);
        }
        

        // bigJobs = MyMath.dynamicKnapsack(bigJobs, weight, profit, bigJobs.length, I.getM(), I, d);
        MDKnapsack kS = new MDKnapsack();
        // int p1 = 0;
        List<Job> shelf1 = new ArrayList<>();
        shelf2.clear();
        List<Job> sequentialJobs = new ArrayList<>();
        smallJobs.clear();
        Vector3D capacity = new Vector3D(I.getM(), 2* I.getL(), invDelta * I.getL()*I.getN());
        kS.solve(knapsackItems, capacity, shelf1, shelf2, smallJobs, sequentialJobs);

        // calculate the work for the jobs in the shelves for the malleable machines.
        double Ws = 0;
        double WShelf1 = 0;
        double WShelf2 = 0;
        int p1 = 0;
        for(Job job : smallJobs) {
            Ws += job.getProcessingTime(1);
        }
        for(Job job : shelf1) {
            int machines = job.canonicalNumberMachines(d);
            p1 += machines;
            job.setAllotedMachines(machines);
            WShelf1 += job.getAllotedMachines() * job.getProcessingTime(job.getAllotedMachines()); //update the work of shelf2
        }
        for(Job job : shelf2) {
            job.setAllotedMachines(job.canonicalNumberMachines(d/2));
            WShelf2 += job.getAllotedMachines() * job.getProcessingTime(job.getAllotedMachines()); //update the work of shelf2
        }

        if(WShelf1 + WShelf2 > I.getM() * d - Ws) {   //there cant exists a schedule of with makespan (s. Thesis Felix S. 76)
            return false;
        }

        // apply the applyTransformationRules
        List<Job> shelf0 = applyTransformationRules(d, shelf1, shelf2, p1);
        // List<Job> shelf0 = applyTransformationRules(d, shelf1, shelf2, p1);
        addSmallJobs(shelf1, shelf2, smallJobs, d, I.getM());

        List<Machine> machinesS0 = new ArrayList<>();
        double startTime = -1;
        for(Job job : shelf0) {
            if(job.getStartingTime() != startTime) {
                Machine m = new Machine(0);
                m.addJob(job);
                machinesS0.add(m);
                startTime = job.getProcessingTime(job.getAllotedMachines());
            } else {
                machinesS0.get(machinesS0.size() - 1).addJob(job);
                startTime += job.getProcessingTime(job.getAllotedMachines());
            }
        }
        I.addMachines(machinesS0);

        //use LPT on the sequential shelf to schedule those jobs
        LongestProcessingTime.LPT(sequentialJobs, I);

        return true;
    };

}
