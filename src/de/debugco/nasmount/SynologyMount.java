package de.debugco.nasmount;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class SynologyMount {
    private String hostAndPortUrl;
    private String loginCookie;

    public SynologyMount(String hostAndPortUrl) {
        this.hostAndPortUrl = hostAndPortUrl;
    }

    private void login(String user, String password) throws FailureException {
        try {
            Map<String, List<String>> headers = new HashMap<String, List<String>>();
            JSONObject object = request(hostAndPortUrl + "/webman/modules/login.cgi", "POST", "username=" + URLEncoder.encode(user, "UTF-8") + "&passwd=" + URLEncoder.encode(password, "UTF-8"), null, headers);
            if(!"success".equals(object.get("result"))) {
                throw new FailureException("Could not login");
            }

            List<String> s = headers.get("Set-Cookie");
            String cookies = s.get(0);
            String[] cookie = cookies.split(";"); // id=<theid>;path=/
            this.loginCookie = cookie[0];
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONArray getShares() throws FailureException {
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("Cookie", loginCookie);

        JSONObject object = request(hostAndPortUrl + "/webman/modules/ControlPanel/modules/shareman.cgi?action=enum_shares&sharetype=enc_all", "GET", null, requestHeaders, null); // &_dc=1273764883218
        if(!Boolean.TRUE.equals(object.get("success"))) {             
            throw new FailureException("Could not get shares");
        }
        return (JSONArray) object.get("shares");
    }

    @SuppressWarnings(value = "unchecked")
    private boolean decryptShare(String name, String password) {
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("Cookie", loginCookie);

        JSONObject parameters = new JSONObject();
        parameters.put("action", "decrypt");
        parameters.put("name", name);
        parameters.put("method", "manual");
        parameters.put("passwd", password);

        JSONObject object = request(hostAndPortUrl + "/webman/modules/ControlPanel/modules/shareman.cgi", "POST", parameters.toJSONString(), requestHeaders, null);

        return Boolean.TRUE.equals(object.get("success"));
    }

    private boolean tryDecryptShares(String mountPassword) throws FailureException {
        JSONArray shares = getShares();
        boolean decryptOk = true;
        for(Object object : shares) {
            if(!(object instanceof JSONObject))
                continue;
            JSONObject json = (JSONObject) object;
            String name = (String) json.get("name");
            if(Long.valueOf(1).equals(json.get("encryption"))) {
                if(mountPassword == null)
                    return false;
                decryptOk &= decryptShare(name, mountPassword);
            }
        }

        return decryptOk;
    }

    private static String showPasswordDialog(String question) {
        JOptionPane pane = new JOptionPane("Question", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        pane.setComponentOrientation(JOptionPane.getRootFrame().getComponentOrientation());
        pane.setWantsInput(true);

        final JDialog dlg = new JDialog(JOptionPane.getFrameForComponent(pane), question, true);
        dlg.setLocationRelativeTo(pane);

        JPanel panel = new JPanel(new FlowLayout());

        panel.add(new JLabel(question));
        final JPasswordField field = new JPasswordField();
        field.setColumns(20);
        field.setMinimumSize(new Dimension(200, 20));

        panel.add(field);
        JButton button = new JButton("Ok");
        dlg.getRootPane().setDefaultButton(button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dlg.setVisible(false);
            }
        });

        panel.add(button);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        panel.add(cancelButton);

        dlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                field.requestFocus();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        dlg.add(panel);
        dlg.pack();

        dlg.setVisible(true);
        dlg.dispose();
        
        return new String(field.getPassword());
    }

    private static JSONObject request(String url, String method, String postData, Map<String, String> requestHeaders, Map<String, List<String>> responseHeaders) {
        try {
            URL u = new URL(url);

            URLConnection c = u.openConnection();
            if(!(c instanceof HttpURLConnection)) {
                throw new RuntimeException("no httpurlconnection");
            }

            if(requestHeaders != null) {
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    c.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if("POST".equals(method)) {
                c.setDoOutput(true);
                ((HttpURLConnection) c).setRequestMethod(method);

                OutputStreamWriter out = new OutputStreamWriter(c.getOutputStream());
                try {
                    out.write(postData);
                } finally {
                    out.close();
                }
            }



            if(responseHeaders != null) {
                for(Map.Entry<String, List<String>> entry : c.getHeaderFields().entrySet()) {
                    responseHeaders.put(entry.getKey(), entry.getValue());
                }
            }


            InputStreamReader streamReader = new InputStreamReader(c.getInputStream());
            try {
                return (JSONObject)JSONValue.parse(streamReader);
            } finally {
                streamReader.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) throws Exception {
        JDialog.setDefaultLookAndFeelDecorated(true);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        if(args.length == 0) {
            JOptionPane.showMessageDialog(null, "Syntax <url> [admin-password]\nWhere <url> is something like: http://nas:5000", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            SynologyMount mount = new SynologyMount(args[0]);

            String password = "";
            if(args.length > 1) {
                password = args[1];
            } else {
                password = showPasswordDialog("Admin-Password");
            }

            try {
                mount.login("admin", password);
            } catch (FailureException e) {
                JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // encryption -
            // 0 = not encrypted
            // 1 = encrypted and not mounted
            // 2 = encrypted and mounted
            String mountPw = null;
            while(!mount.tryDecryptShares(mountPw)) {
                mountPw = showPasswordDialog("Share-Password");
            }
        } catch(Exception e) {
            JOptionPane.showMessageDialog(null, "Exception: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
