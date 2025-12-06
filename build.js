const { exec } = require('child_process');
const path = require('path');

console.log('========================================');
console.log('Building Hardcore Minecraft Launcher');
console.log('========================================');
console.log('');

// Устанавливаем переменные окружения для отключения подписи
process.env.CSC_IDENTITY_AUTO_DISCOVERY = 'false';
process.env.WIN_CSC_LINK = '';

console.log('Building launcher executable...');
console.log('(Code signing disabled)');
console.log('');

const buildCommand = 'npm run build-win';

exec(buildCommand, { 
    cwd: __dirname,
    env: { ...process.env, CSC_IDENTITY_AUTO_DISCOVERY: 'false', WIN_CSC_LINK: '' }
}, (error, stdout, stderr) => {
    if (error) {
        console.error(`Error: ${error.message}`);
        process.exit(1);
    }
    
    if (stderr) {
        console.error(`Stderr: ${stderr}`);
    }
    
    console.log(stdout);
    
    console.log('');
    console.log('========================================');
    console.log('Build complete!');
    console.log('Check LauncherExe folder for the .exe file');
    console.log('========================================');
});


