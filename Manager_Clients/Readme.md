Q: How does our program run:
A:
    DIFFERENT RUNS:
    
First navigate to App, Manager, Worker folders serperatly and do mvn clean package at each (WITHOUT THIS, NOTHING WILL WORK).

1) we used this line to run on terminal which runs with 5 inputs given and tasksperworker 100 with no terminate option (-t) 
        and it took : 44 minutes to finish them:
        > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input1.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input2.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input3.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input4.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input5.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/output1.html" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput2.html" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput3.html" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput4.html" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput5.html" 100

    2) two local apps took 23 minutes:
        (while manager node still running from previous interations)
        - 1 input file with 100 tasksperworker and no terminate :
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input1.txt"  "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/output1.html"  100
        - 2 input file with 100 tasksperworker and terminate option 
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input2.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input3.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/output2.html" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput3.html" 100 -t

    3) five local apps took 30 minutes:
        Each with 100 tasksperWorker, the last one with termination command.
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input1.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/output1.html" 100
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input2.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput2.html" 100
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input3.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput3.html" 100
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input4.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput4.html" 100
            > java -jar target/App-1.0.jar "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/input5.txt" "/home/users/bsc/fawziabu/Desktop/DSP/AWS/App/src/main/resources/ouput5.html" 100 -t


    - The ami and type used for the workers and manager instances: 
            * amiId = "ami-00e95a9222311e8ed"
            * type = M4.LARGE

Q: How our program works: 
A:   Our program flow is as follow: 
        1) App makes manager and bucket for the jars and his own bucket for his input.
        2) App uploads the jar to S3 and the inputs.
        3) App sends to the manager his url to inputs. (to each input app sends a message)
        4) If app has termination with args, it sends terminate after few seconds. 
        4) Manager start running, starts a thread for output processing (to be explained in 13).
        5) Manager takes a single message from AppsToManager queue.
        6) takes the url from the message and downloads the file.
        7) creates 2 queues for each message, InputQueueX & OutputQueueX (while X is number that increases with each message).
        8) Manager sends the content of the input (review by review) to the inputqueue.
        9) Manager sends message with inputUrlQueue ouputUrlQueue structure to ManagerToWorkers queue.
        10) Manager creates instances for workers: NumOfReview/TasksPerWorker.
        11) Workers starts to take a message from ManagerToWorker queue, saves the inputqueue and ouputqueue for the reviews' input.
        12) Workers takes a message from the inputqueue, processes it and sends the result to outputqueue.
        13) Output thread starts to work on outputqueue once the message in the output is the same as was in the inputQueue of the reviews' input.
        14) Takes the messages out of the outputqueue, creating HTML file with it.
        15) Uploads the file to S3 and sends a message with the url to ManagerToApps Queue.
        16) App receives a message stating the location of the url, and downloads the file.
        17) Once the app gets all the ouput for each input it ends the process of it.
        18) If manager takes termination from app, manager terminates himself.
        19) Once the queue of the managerToWorkers is empty, the worker terminate himself.
        20) FOR CRISIS ISSUES: if there is a file that has not been processed but there is no worker (worker fail), manager revives one.


Q: Did you think for more than 2 minutes about security? 
A: credintials are hidden in nano file : ~/.aws/credintials, sending them to manager is by accessing the file only.


Q: Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? 
Scalability is very important aspect of the system; be sure it is scalable!

A: Yes, our program runs in parrellel for each input, and it does not take each input in a row and starts the second after finishing the first,
    each client has his own bucket, queues so no merge will happen.


Q: What about persistence? What if a node dies? What if a node stalls for a while? Have you taken care of all possible outcomes in the system? 
Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!
A: Once worker dies, and there is no worker to apply the job we revived one to complete the job, we used visibility techinqe implemnted by aws
    to avoid lossing message, so no message will vanish.

Q: Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!
A: We used a thread to process the output, and the main thread for the input. Threads are good if there is a big chance of sharing in same object, 
    with importance to order or the connection between the samples.

Q: Did you run more than one client at the same time? Be sure they work properly, and finish
properly, and your results are correct.

A: Yes.

Q: Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
A: Yes.

Q: Did you manage the termination process? Be sure all is closed once requested!
A: Yes!

Q: Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
A: Yes, we used 9 instances as max, and used SQS and S3.

Q: Are all your workers working hard? Or some are slacking? Why?
A: Working hard giving their best but with maximum number of workers for each file,
    each worker has a message to work at the file so it does not stop taking msesages.

Q:Is your manager doing more work than he's supposed to? Have you made sure each part of
your system has properly defined tasks? Did you mix their tasks? Don't!

A: no, tasks are applied by workers, apps take the output of s3 and raises the input file to s3, so manager manages only the communication.

Q: Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another?
A: Yes, distrbited means that we split the tasks between instances and each one done his task. No one is waiting.
