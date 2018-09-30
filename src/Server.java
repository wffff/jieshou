import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class Server {
    static ServerFrame sframe = null;
    //静态ArrayList存储所有uid，uid由ip和端口字符串拼接而成
    static ArrayList<String> uid_arr = new ArrayList<String>();
    //静态HashMap存储所有uid, ServerThread对象组成的对
    static HashMap<String, ServerThread> hm = new HashMap<String, ServerThread>();
    static StringBuilder uid = new StringBuilder("");

    public static void main(String[] args) throws Exception {
        QueueProducer queueProducer = new QueueProducer();
        //建立服务器ServerSocket
        ServerSocket ss = new ServerSocket(9000);
        //创建客户端窗口对象
        sframe = new ServerFrame();
        //窗口关闭键无效，必须通过退出键退出客户端以便善后
        sframe.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        //获取本机屏幕横向分辨率
        int w = Toolkit.getDefaultToolkit().getScreenSize().width;
        //获取本机屏幕纵向分辨率
        int h = Toolkit.getDefaultToolkit().getScreenSize().height;
        //将窗口置中
        sframe.setLocation((w - sframe.WIDTH) / 2, (h - sframe.HEIGHT) / 2);
        //设置客户端窗口为可见
        sframe.setVisible(true);

        //提示Server建立成功
        sframe.jtaChat.append("Server online... " + ss.getInetAddress().getLocalHost().getHostAddress() + ", " + 9000 + "\n");
        //监听端口，建立连接并开启新的ServerThread线程来服务此连接
        while (true) {
            //接收客户端Socket
            Socket s = ss.accept();
//            s.setKeepAlive(true);
            s.setSoTimeout(60 * 1000);
            //提取客户端IP和端口
            String ip = s.getInetAddress().getHostAddress();
            int port = s.getPort();
            byte[] buf = new byte[1024];
            int len = 0;
            InputStream inputStream = s.getInputStream();
            //持续监听并转发客户端消息
            len = inputStream.read(buf);
            if (len != -1 && len == 11) {
                System.out.println(len);
                String msg = new String(buf, 0, len);
                //建立新的服务器线程, 向该线程提供服务器ServerSocket，客户端Socket，客户端IP和端口
                new Thread(new ServerThread(s, ss, ip, port, msg, queueProducer)).start();
            } else {
                s.close();
            }
        }
    }

}

class t1 implements Runnable {
    String s;
    OutputStream out;

    public t1(String s, OutputStream out) {
        this.s = s;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            while (true) {
                out.write(ServerFrame.hex2byte(s));
                Thread.sleep(5000);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class ServerThread implements Runnable {
    //获取的客户端Socket
    Socket s = null;
    //获取的服务器ServerSocket
    ServerSocket ss = null;
    //获取的客户端IP
    String ip = null;
    //获取的客户端端口
    int port = 0;
    //组合客户端的ip和端口字符串得到uid字符串
    String uid = null;

    String name = null;
    QueueProducer queueProducer = null;

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ServerThread(Socket s, ServerSocket ss, String ip, int port, String name, QueueProducer queueProducer) {
        this.s = s;
        this.ss = ss;
        this.ip = ip;
        this.port = port;
        uid = ip + ":" + port;
        this.name = name;
        this.queueProducer = queueProducer;
    }

    public static final String bytesToHexString(byte[] bArray, int length) {
        StringBuffer sb = new StringBuffer(length);
        String sTemp;
        for (int i = 0; i < length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }


    @Override
    public void run() {
        //将当前客户端uid存入ArrayList
        Server.uid_arr.add(uid);
        //将当前uid和ServerThread对存入HashMap
        Server.hm.put(uid, this);

        //时间显示格式
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        //控制台打印客户端IP和端口
        System.out.println("Client connected: " + uid);

        try {
            //获取输入流
            InputStream in = s.getInputStream();
            //获取输出流
            OutputStream out = s.getOutputStream();

            //向当前客户端传输连接成功信息
            String welcome = sdf.format(new Date()) + "\n成功连接服务器...\n服务器IP: " + ss.getInetAddress().getLocalHost().getHostAddress() + ", 端口: 10000\n客户端IP: " + ip + ", 端口: " + port + "\n";
            out.write(welcome.getBytes());

            //广播更新在线名单
            DefaultTableModel tbm = (DefaultTableModel) Server.sframe.jtbOnline.getModel();
            //清除在线名单列表
            tbm.setRowCount(0);
            //更新在线名单
            for (ServerThread s : Server.hm.values()) {
                String[] tmp = new String[3];
                //如果是自己则不在名单中显示
                tmp[0] = s.name;
                tmp[1] = s.ip;
                tmp[2] = String.valueOf(s.port);
                //添加当前在线者之一
                tbm.addRow(tmp);
            }
            //提取在线列表的渲染模型
            DefaultTableCellRenderer tbr = new DefaultTableCellRenderer();
            //表格数据居中显示
            tbr.setHorizontalAlignment(JLabel.CENTER);
            Server.sframe.jtbOnline.setDefaultRenderer(Object.class, tbr);

            new Thread(new t1("01 03 01 2C 00 08 84 39", out)).start();

            //准备缓冲区
            byte[] buf = new byte[1024];
            int len = 0;
            //持续监听并转发客户端消息
            while (true) {
                len = in.read(buf);
                if (len != -1) {
                    String msg = new String(buf, 0, len);
                    if (!msg.equals("test")) {
                        String m = bytesToHexString(buf, len);
                        Date date = new Date();
                        queueProducer.doSend(name + "#" + m + "%" + df.format(date));
                        Server.sframe.jtaChat.append(uid + "Say:" + m +df.format(date)+ "\n");
                    }
                }
            }
        } catch (Exception e) {
            try {
                s.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            //将当前客户端uid存入ArrayList
            Server.uid_arr.remove(uid);
            //将当前uid和ServerThread对存入HashMap
            Server.hm.remove(uid);
            //广播更新在线名单
            DefaultTableModel tbm = (DefaultTableModel) Server.sframe.jtbOnline.getModel();
            //清除在线名单列表
            tbm.setRowCount(0);
            for (ServerThread s : Server.hm.values()) {
                String[] tmp = new String[3];
                //如果是自己则不在名单中显示
                tmp[0] = s.name;
                tmp[1] = s.ip;
                tmp[2] = String.valueOf(s.port);
                //添加当前在线者之一
                tbm.addRow(tmp);
            }
            //提取在线列表的渲染模型
            DefaultTableCellRenderer tbr = new DefaultTableCellRenderer();
            //表格数据居中显示
            tbr.setHorizontalAlignment(JLabel.CENTER);
            Server.sframe.jtbOnline.setDefaultRenderer(Object.class, tbr);
        }
    }
}

//服务端窗口
class ServerFrame extends JFrame {
    //时间显示格式
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    //窗口宽度
    final int WIDTH = 700;
    //窗口高度
    final int HEIGHT = 700;

    //创建发送按钮
    JButton btnSend = new JButton("发送");
    //创建发送16进制按钮
    JButton btnSend16 = new JButton("十六");
    //创建清除按钮
    JButton btnClear = new JButton("清屏");
    //创建退出按钮
    JButton btnExit = new JButton("退出");

    //创建消息接收者标签
    JLabel lblReceiver = new JLabel("对谁说？");

    //创建文本输入框, 参数分别为行数和列数
    JTextArea jtaSay = new JTextArea();

    //创建聊天消息框
    JTextArea jtaChat = new JTextArea();

    //当前在线列表的列标题
    String[] colTitles = {"网名", "IP", "端口"};
    //当前在线列表的数据
    String[][] rowData = null;
    //创建当前在线列表
    JTable jtbOnline = new JTable
            (
                    new DefaultTableModel(rowData, colTitles) {
                        //表格不可编辑，只可显示
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    }
            );


    //创建聊天消息框的滚动窗
    JScrollPane jspChat = new JScrollPane(jtaChat);

    //创建当前在线列表的滚动窗
    JScrollPane jspOnline = new JScrollPane(jtbOnline);

    //设置默认窗口属性，连接窗口组件
    public ServerFrame() {
        //标题
        setTitle("圣能科技");
        //大小
        setSize(WIDTH, HEIGHT);
        //不可缩放
        setResizable(false);
        //设置布局:不适用默认布局，完全自定义
        setLayout(null);

        //设置按钮大小和位置
        btnSend.setBounds(20, 600, 80, 60);
        btnSend16.setBounds(120, 600, 80, 60);
        btnClear.setBounds(220, 600, 80, 60);
        btnExit.setBounds(320, 600, 80, 60);

        //设置标签大小和位置
        lblReceiver.setBounds(20, 420, 300, 30);

        //设置按钮文本的字体
        btnSend.setFont(new Font("宋体", Font.BOLD, 18));
        btnSend16.setFont(new Font("宋体", Font.BOLD, 18));
        btnClear.setFont(new Font("宋体", Font.BOLD, 18));
        btnExit.setFont(new Font("宋体", Font.BOLD, 18));

        //添加按钮
        this.add(btnSend);
        this.add(btnSend16);
        this.add(btnClear);
        this.add(btnExit);

        //添加标签
        this.add(lblReceiver);

        //设置文本输入框大小和位置
        jtaSay.setBounds(20, 460, 360, 120);
        //设置文本输入框字体
        jtaSay.setFont(new Font("楷体", Font.BOLD, 16));
        //添加文本输入框
        this.add(jtaSay);

        //聊天消息框自动换行
        jtaChat.setLineWrap(true);
        //聊天框不可编辑，只用来显示
        jtaChat.setEditable(false);
        //设置聊天框字体
        jtaChat.setFont(new Font("楷体", Font.BOLD, 16));

        //设置滚动窗的水平滚动条属性:不出现
        jspChat.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        //设置滚动窗的垂直滚动条属性:需要时自动出现
        jspChat.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        //设置滚动窗大小和位置
        jspChat.setBounds(20, 20, 360, 400);
        //添加聊天窗口的滚动窗
        this.add(jspChat);
        //设置滚动窗的水平滚动条属性:不出现
        jspOnline.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        //设置滚动窗的垂直滚动条属性:需要时自动出现
        jspOnline.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        //设置当前在线列表滚动窗大小和位置
        jspOnline.setBounds(420, 20, 250, 400);
        //添加当前在线列表
        this.add(jspOnline);

        //添加发送按钮的响应事件
        btnSend.addActionListener
                (
                        new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                //显示最新消息
                                jtaChat.setCaretPosition(jtaChat.getDocument().getLength());
                                try {
//                                    有收信人才发送
                                    if (Server.uid.toString().equals("") == false) {
//                                        在聊天窗打印发送动作信息
                                        jtaChat.append(sdf.format(new Date()) + "\n发往 " + Server.uid.toString() + ":\n");
//                                        显示发送消息
                                        jtaChat.append(jtaSay.getText() + "\n");
//                                        向服务器发送聊天信息
                                        String s = Server.uid.toString();
                                        String[] split = s.split(",");
                                        for (String sb : split) {
                                            ServerThread serverThread = Server.hm.get(sb);
                                            OutputStream out = serverThread.s.getOutputStream();
                                            out.write((jtaSay.getText()).getBytes());
                                        }
                                    }
                                } catch (Exception e) {
                                } finally {
//                                    文本输入框清除
                                    jtaSay.setText("");
                                }
                            }
                        }
                );

        //添加发送按钮的响应事件
        btnSend16.addActionListener
                (
                        new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                //显示最新消息
                                jtaChat.setCaretPosition(jtaChat.getDocument().getLength());
                                try {
//                                    有收信人才发送
                                    if (Server.uid.toString().equals("") == false) {
//                                        在聊天窗打印发送动作信息
                                        jtaChat.append(sdf.format(new Date()) + "\n发往 " + Server.uid.toString() + ":\n");
//                                        显示发送消息
                                        jtaChat.append(jtaSay.getText() + "\n");
//                                        向服务器发送聊天信息
                                        String s = Server.uid.toString();
                                        String[] split = s.split(",");
                                        for (String sb : split) {
                                            ServerThread serverThread = Server.hm.get(sb);
                                            OutputStream out = serverThread.s.getOutputStream();
                                            out.write(hex2byte(jtaSay.getText()));
                                        }
                                    }
                                } catch (Exception e) {
                                } finally {
//                                    文本输入框清除
                                    jtaSay.setText("");
                                }
                            }
                        }
                );
        //添加清屏按钮的响应事件
        btnClear.addActionListener
                (
                        new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                //聊天框清屏
                                jtaChat.setText("");
                            }
                        }
                );
        //添加退出按钮的响应事件
        btnExit.addActionListener
                (
                        new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                try {
//                                    //向服务器发送退出信息
//                                    OutputStream out = Client1.s.getOutputStream();
//                                    out.write("Exit/".getBytes());
//                                    //退出
                                    System.exit(0);
                                } catch (Exception e) {
                                }
                            }
                        }
                );
        //添加在线列表项被鼠标选中的相应事件
        jtbOnline.addMouseListener
                (
                        new MouseListener() {
                            @Override
                            public void mouseClicked(MouseEvent event) {
                                //取得在线列表的数据模型
                                DefaultTableModel tbm = (DefaultTableModel) jtbOnline.getModel();
                                //提取鼠标选中的行作为消息目标，最少一个人，最多全体在线者接收消息
                                int[] selectedIndex = jtbOnline.getSelectedRows();
                                //将所有消息目标的uid拼接成一个字符串, 以逗号分隔
                                Server.uid = new StringBuilder("");
                                for (int i = 0; i < selectedIndex.length; i++) {
                                    Server.uid.append((String) tbm.getValueAt(selectedIndex[i], 1));
                                    Server.uid.append(":");
                                    Server.uid.append((String) tbm.getValueAt(selectedIndex[i], 2));
                                    if (i != selectedIndex.length - 1)
                                        Server.uid.append(",");
                                }
                                lblReceiver.setText("发给：" + Server.uid.toString());
                            }

                            @Override
                            public void mousePressed(MouseEvent event) {
                            }

                            ;

                            @Override
                            public void mouseReleased(MouseEvent event) {
                            }

                            ;

                            @Override
                            public void mouseEntered(MouseEvent event) {
                            }

                            ;

                            @Override
                            public void mouseExited(MouseEvent event) {
                            }

                            ;
                        }
                );
    }

    public static byte[] hex2byte(String hex) {
        String digital = "0123456789ABCDEF";
        String hex1 = hex.replace(" ", "");
        char[] hex2char = hex1.toCharArray();
        byte[] bytes = new byte[hex1.length() / 2];
        byte temp;
        for (int p = 0; p < bytes.length; p++) {
            temp = (byte) (digital.indexOf(hex2char[2 * p]) * 16);
            temp += digital.indexOf(hex2char[2 * p + 1]);
            bytes[p] = (byte) (temp & 0xff);
        }
        return bytes;
    }

}