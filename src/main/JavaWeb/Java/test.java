import java.io.BufferedReader;
import java.io.InputStreamReader;

public class test {

    public static void main(String[] args) {
        try {
            String pythonPath = "C:\\Users\\HuangZhongjie\\OneDrive\\Zhongjie_Huang_s_Database\\Academic_Finishing\\Academic_Year_Three\\Dissertation_Project\\Python\\python.exe";

            String pythonScriptPath = "C:\\Users\\HuangZhongjie\\OneDrive\\Zhongjie_Huang_s_Database\\Investment_and_Financial_Management\\src\\main\\Python\\FacialEmotionRecognition\\test.py";

            String userName = "Zhongjie";

            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, pythonScriptPath, userName);

            Process process = processBuilder.start();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                System.out.println("Python output: " + line);
            }

            process.waitFor();
        } catch (Exception e) {
            System.out.println();
        }
    }

    // to be written in the future

}
