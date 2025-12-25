import * as React from 'react';
import PropTypes from 'prop-types';
// import Box from '@mui/material/Box';
import { createTheme } from '@mui/material/styles';
import { AppProvider } from '@toolpad/core/AppProvider';
import { DashboardLayout } from '@toolpad/core/DashboardLayout';
import { DemoProvider, useDemoRouter } from '@toolpad/core/internal';
import DashboardIcon from '@mui/icons-material/Dashboard';
import EditIcon from '@mui/icons-material/Edit';
import BarChartIcon from '@mui/icons-material/BarChart';
import LogoutIcon from '@mui/icons-material/Logout';
import { Button } from '@mui/material';
import ReadPage from './ReadPage';
import WritePage from './EditPage';
import MetricsPage from './MetricsPage';

const NAVIGATION = [
  { kind: 'header', title: 'Main items' },
  { segment: 'read', title: 'Read', icon: <DashboardIcon /> },
  { segment: 'write', title: 'Write', icon: <EditIcon /> },
  { segment: 'metrics', title: 'Metrics', icon: <BarChartIcon /> },
];

const demoTheme = createTheme({
  cssVariables: { colorSchemeSelector: 'data-toolpad-color-scheme' },
  colorSchemes: { light: true, dark: true },
  breakpoints: { values: { xs: 0, sm: 600, md: 600, lg: 1200, xl: 1536 } },
});

function DemoPageContent({ pathname }) {
  switch (pathname) {
    case '/read':
      return <ReadPage />;
    case '/write':
      return <WritePage />;
    case '/metrics':
      return <MetricsPage />;
    default:
      return <ReadPage />;
  }
}

DemoPageContent.propTypes = { pathname: PropTypes.string.isRequired };

function Home() {
  const router = useDemoRouter('read');

  const handleLogOut = async () => {
    try {
      await fetch('http://localhost:8080/auth/logout', {
        method: 'POST',
        credentials: 'include', // Include cookies
      });
    } catch (error) {
      console.error('Logout error:', error);
    }

    localStorage.removeItem('auth');
    localStorage.removeItem('token');
    sessionStorage.clear();

    // Redirect to login page
    window.location.href = '/login';
  };

  return (
    <DemoProvider>
      <AppProvider
        navigation={NAVIGATION}
        router={router}
        theme={demoTheme}
        branding={{
          title: 'Zookeeper',
          logo: <img src="/zookeeper.png" alt="logo" />,
        }}
      >
        <DashboardLayout
          slots={{
            toolbarActions: () => (
              <Button
                variant="contained"
                color="error"
                endIcon={<LogoutIcon />}
                onClick={handleLogOut}
              >
                Logout
              </Button>
            ),
          }}
        >
          <DemoPageContent pathname={router.pathname} />
        </DashboardLayout>
      </AppProvider>
    </DemoProvider>
  );
}

export default Home;
