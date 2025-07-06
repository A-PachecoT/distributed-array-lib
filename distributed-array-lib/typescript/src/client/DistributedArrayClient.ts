import * as net from 'net';
import * as readline from 'readline';
import { Message, MessageBuilder, MessageType } from '../common/Message';

export class DistributedArrayClient {
    private masterHost: string;
    private masterPort: number;

    constructor(masterHost: string, masterPort: number) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    async createIntArray(arrayId: string, size: number): Promise<void> {
        const data = Array.from({ length: size }, () => Math.floor(Math.random() * 1000) + 1);
        
        const message = MessageBuilder.create(
            MessageType.CREATE_ARRAY,
            "client",
            "master",
            {
                arrayId,
                dataType: "int",
                values: data
            }
        );

        const response = await this.sendMessage(message);
        console.log(`Create array response: ${response}`);
    }

    async createDoubleArray(arrayId: string, size: number): Promise<void> {
        const data = Array.from({ length: size }, () => Math.random() * 99 + 1);
        
        const message = MessageBuilder.create(
            MessageType.CREATE_ARRAY,
            "client",
            "master",
            {
                arrayId,
                dataType: "double",
                values: data
            }
        );

        const response = await this.sendMessage(message);
        console.log(`Create array response: ${response}`);
    }

    async applyOperation(arrayId: string, operation: string): Promise<void> {
        const message = MessageBuilder.create(
            MessageType.APPLY_OPERATION,
            "client",
            "master",
            {
                arrayId,
                operation
            }
        );

        const response = await this.sendMessage(message);
        console.log(`Apply operation response: ${response}`);
    }

    async getResult(arrayId: string): Promise<void> {
        const message = MessageBuilder.create(
            MessageType.GET_RESULT,
            "client",
            "master",
            {
                arrayId
            }
        );

        const response = await this.sendMessage(message);
        console.log(`Get result response: ${response}`);
    }

    private sendMessage(message: Message): Promise<string> {
        return new Promise((resolve, reject) => {
            const client = new net.Socket();
            
            client.connect(this.masterPort, this.masterHost, () => {
                client.write(MessageBuilder.toJSON(message) + '\n');
            });

            client.on('data', (data) => {
                resolve(data.toString().trim());
                client.destroy();
            });

            client.on('error', (err) => {
                reject(err);
            });

            client.on('close', () => {
                // Connection closed
            });
        });
    }
}

// CLI Interface
async function main() {
    const args = process.argv.slice(2);
    
    if (args.length < 2) {
        console.log('Usage: ts-node DistributedArrayClient.ts <masterHost> <masterPort>');
        console.log('\nCommands:');
        console.log('  create-int <array_id> <size>');
        console.log('  create-double <array_id> <size>');
        console.log('  apply <array_id> <operation>');
        console.log('  get <array_id>');
        process.exit(1);
    }

    const masterHost = args[0];
    const masterPort = parseInt(args[1]);
    const client = new DistributedArrayClient(masterHost, masterPort);

    console.log(`Connected to master at ${masterHost}:${masterPort}`);
    console.log('Enter commands (type "help" for usage, "exit" to quit):');

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: '> '
    });

    rl.prompt();

    rl.on('line', async (line) => {
        const parts = line.trim().split(' ');
        
        if (parts.length === 0 || parts[0] === '') {
            rl.prompt();
            return;
        }

        try {
            switch (parts[0]) {
                case 'create-int':
                    if (parts.length >= 3) {
                        await client.createIntArray(parts[1], parseInt(parts[2]));
                    } else {
                        console.log('Usage: create-int <array_id> <size>');
                    }
                    break;

                case 'create-double':
                    if (parts.length >= 3) {
                        await client.createDoubleArray(parts[1], parseInt(parts[2]));
                    } else {
                        console.log('Usage: create-double <array_id> <size>');
                    }
                    break;

                case 'apply':
                    if (parts.length >= 3) {
                        await client.applyOperation(parts[1], parts[2]);
                    } else {
                        console.log('Usage: apply <array_id> <operation>');
                    }
                    break;

                case 'get':
                    if (parts.length >= 2) {
                        await client.getResult(parts[1]);
                    } else {
                        console.log('Usage: get <array_id>');
                    }
                    break;

                case 'help':
                    console.log('\nCommands:');
                    console.log('  create-int <array_id> <size> - Create integer array');
                    console.log('  create-double <array_id> <size> - Create double array');
                    console.log('  apply <array_id> <operation> - Apply operation (example1 or example2)');
                    console.log('  get <array_id> - Get result');
                    console.log('  exit - Quit');
                    break;

                case 'exit':
                    console.log('Goodbye!');
                    process.exit(0);

                default:
                    console.log('Unknown command. Type "help" for usage.');
            }
        } catch (error) {
            console.error(`Error: ${error}`);
        }

        rl.prompt();
    });
}

if (require.main === module) {
    main().catch(console.error);
}